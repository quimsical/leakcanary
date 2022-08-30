package org.leakcanary

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import dev.leakcanary.sqldelight.App
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import shark.HeapAnalysis
import shark.HeapAnalysisFailure
import shark.HeapAnalysisSuccess
import shark.LibraryLeak

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

  fun listClientApps(): Flow<List<App>> {
    // TODO Should dispatch on a dispatcher with a thread pool of the same size
    // as the connection pool. If we do this, we'd need to wrap all operations (including sync ones)
    return db.appQueries.selectAll().asFlow().mapToList(Dispatchers.IO)
  }
}
