package org.leakcanary.data

import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOne
import dev.leakcanary.sqldelight.App
import dev.leakcanary.sqldelight.RetrieveLeakBySignature
import dev.leakcanary.sqldelight.SelectAllByApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.leakcanary.Database
import org.leakcanary.data.DatabaseModule.DatabaseReadDispatcher
import org.leakcanary.data.DatabaseModule.DatabaseWriteDispatcher
import org.leakcanary.util.Serializables
import org.leakcanary.util.toByteArray
import shark.HeapAnalysis
import shark.HeapAnalysisFailure
import shark.HeapAnalysisSuccess
import shark.LibraryLeak

class HeapRepository @Inject constructor(
  private val db: Database,
  private val sqlDriver: SqlDriver,
  @DatabaseReadDispatcher private val dbReadDispatcher: CoroutineDispatcher,
  @DatabaseWriteDispatcher private val dbWriteDispatcher: CoroutineDispatcher
) {

  fun insertHeapAnalysis(packageName: String, heapAnalysis: HeapAnalysis): Long {
    return runBlocking(dbWriteDispatcher) {
      db.transactionWithResult {
        db.appQueries.insertOrIgnore(packageName)
        when (heapAnalysis) {
          is HeapAnalysisFailure -> {
            val cause = heapAnalysis.exception.cause!!
            db.heapAnalysisQueries.insertFailure(
              app_package_name = packageName,
              created_at_time_millis = heapAnalysis.createdAtTimeMillis,
              dump_duration_millis = heapAnalysis.dumpDurationMillis,
              exception_summary = "${cause.javaClass.simpleName} ${cause.message}",
              raw_object = heapAnalysis.toByteArray()
            )
            db.heapAnalysisQueries.lastInsertRowId().executeAsOne()
          }
          is HeapAnalysisSuccess -> {
            val leakCount = heapAnalysis.applicationLeaks.size + heapAnalysis.libraryLeaks.size
            db.heapAnalysisQueries.insertSuccess(
              app_package_name = packageName,
              created_at_time_millis = heapAnalysis.createdAtTimeMillis,
              dump_duration_millis = heapAnalysis.dumpDurationMillis,
              leak_count = leakCount,
              raw_object = heapAnalysis.toByteArray(),
            )
            val heapAnalysisId = db.heapAnalysisQueries.lastInsertRowId().executeAsOne()
            heapAnalysis.allLeaks.forEach { leak ->
              db.leakQueries.insert(
                signature = leak.signature,
                short_description = leak.shortDescription,
                is_library_leak = leak is LibraryLeak
              )
              leak.leakTraces.forEachIndexed { index, leakTrace ->
                db.leakTraceQueries.insert(
                  class_simple_name = leakTrace.leakingObject.classSimpleName,
                  leak_trace_index = index,
                  heap_analysis_id = heapAnalysisId,
                  leak_signature = leak.signature
                )
              }
            }
            heapAnalysisId
          }
        }.also {
          db.appQueries.updateLeakCounts()
        }
      }
    }
  }

  suspend fun markAsRead(signature: String) {
    withContext(dbWriteDispatcher) {
      // Custom impl to avoid triggering listeners.
      sqlDriver.execute(
        identifier = null,
        sql = """
    |UPDATE leak
    |SET is_read = 1
    |WHERE signature = ?
    """.trimMargin(),
        parameters = 1,
        binders = {
          bindString(1, signature)
        }
      )
    }
  }

  fun listAppAnalyses(packageName: String): Flow<List<SelectAllByApp>> {
    return db.heapAnalysisQueries.selectAllByApp(packageName).asFlow().mapToList(dbReadDispatcher)
  }

  fun listClientApps(): Flow<List<App>> {
    return db.appQueries.selectAll().asFlow().mapToList(dbReadDispatcher)
  }

  // TODO Handle error, this will throw NPE if the analysis doesn't exist
  fun getHeapAnalysis(heapAnalysisId: Long): Flow<HeapAnalysis> {
    return db.heapAnalysisQueries.selectById(heapAnalysisId).asFlow()
      .mapToOne(dbReadDispatcher).map { Serializables.fromByteArray<HeapAnalysis>(it)!! }
  }

  fun getLeakReadStatuses(heapAnalysisId: Long): Flow<Map<String, Boolean>> {
    return db.leakQueries.retrieveLeakReadStatuses(heapAnalysisId).asFlow()
      .mapToList(dbReadDispatcher)
      .map { it.associate { (signature, isRead) -> signature to isRead } }
  }

  fun getLeak(leakSignature: String): Flow<List<RetrieveLeakBySignature>> {
    return db.leakTraceQueries.retrieveLeakBySignature(leakSignature)
      .asFlow()
      .mapToList(dbReadDispatcher)
  }
}
