package org.leakcanary.data

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOne
import dev.leakcanary.sqldelight.App
import dev.leakcanary.sqldelight.RetrieveLeakReadStatuses
import dev.leakcanary.sqldelight.SelectAllByApp
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import org.leakcanary.Database
import org.leakcanary.util.Serializables
import org.leakcanary.util.toByteArray
import shark.HeapAnalysis
import shark.HeapAnalysisFailure
import shark.HeapAnalysisSuccess
import shark.LibraryLeak

// TODO Should dispatch on a dispatcher with a thread pool of the same size
// as the connection pool. If we do this, we'd need to wrap all operations (including sync ones)
class HeapRepository @Inject constructor(
  private val db: Database
) {

  fun insertHeapAnalysis(packageName: String, heapAnalysis: HeapAnalysis): Long {
    return db.transactionWithResult {
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

  fun listAppAnalyses(packageName: String): Flow<List<SelectAllByApp>> {
    return db.heapAnalysisQueries.selectAllByApp(packageName).asFlow().mapToList(Dispatchers.IO)
  }

  fun listClientApps(): Flow<List<App>> {
    return db.appQueries.selectAll().asFlow().mapToList(Dispatchers.IO)
  }

  // TODO Handle error, this will throw NPE if the analysis doesn't exist
  fun getHeapAnalysis(heapAnalysisId: Long): Flow<HeapAnalysis> {
    return db.heapAnalysisQueries.selectById(heapAnalysisId).asFlow()
      .mapToOne(Dispatchers.IO).map { Serializables.fromByteArray<HeapAnalysis>(it)!! }
  }

  fun getLeakReadStatuses(heapAnalysisId: Long): Flow<Map<String, Boolean>> {
    return db.leakQueries.retrieveLeakReadStatuses(heapAnalysisId).asFlow().mapToList(Dispatchers.IO)
      .map { it.associate { (signature, isRead) -> signature to isRead } }
  }

  fun getAnalysisDetails(heapAnalysisId: Long): Flow<Pair<HeapAnalysisSuccess, List<RetrieveLeakReadStatuses>>> {
     return db.heapAnalysisQueries.selectById(heapAnalysisId)
      .asFlow()
      .mapToOne(Dispatchers.IO)
      .transformLatest { selectByIdResult ->
        val heapAnalysis = Serializables.fromByteArray<HeapAnalysisSuccess>(selectByIdResult)

        // TODO Handle null (deleted or failure)
        heapAnalysis!!

        db.leakQueries.retrieveLeakReadStatuses(heapAnalysisId)
          .asFlow()
          .mapToList(Dispatchers.IO)
          .map { statuses ->
            heapAnalysis to statuses
          }
      }
  }


}
