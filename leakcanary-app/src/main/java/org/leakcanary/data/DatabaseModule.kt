package org.leakcanary.data

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.res.Resources
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.squareup.sqldelight.android.AndroidSqliteDriver
import com.squareup.sqldelight.db.SqlDriver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.newSingleThreadContext
import org.leakcanary.Database

@Module
@InstallIn(SingletonComponent::class)
class DatabaseModule {

  @Qualifier
  @Retention(AnnotationRetention.BINARY)
  annotation class WriteAheadLoggingEnabled

  @Provides @WriteAheadLoggingEnabled
  fun provideWriteAheadLoggingEnabled(app: Application): Boolean {
    val activityManager = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return !activityManager.isLowRamDevice
  }

  @Provides @Singleton fun provideSqliteDriver(
    app: Application, @WriteAheadLoggingEnabled wolEnabled: Boolean
  ): SqlDriver {
    val realFactory = FrameworkSQLiteOpenHelperFactory()
    return AndroidSqliteDriver(
      schema = Database.Schema, factory = { configuration ->
        realFactory.create(configuration).apply { setWriteAheadLoggingEnabled(wolEnabled) }
      }, context = app, name = "leakcanary.db"
    )
  }

  @Provides @Singleton fun provideDatabase(driver: SqlDriver): Database = Database(driver)

  @Qualifier
  @Retention(AnnotationRetention.BINARY)
  annotation class DatabaseWriteDispatcher

  @Qualifier
  @Retention(AnnotationRetention.BINARY)
  annotation class DatabaseReadDispatcher

  @Provides @Singleton @DatabaseWriteDispatcher fun provideDatabaseWriteDispatcher(
    @WriteAheadLoggingEnabled wolEnabled: Boolean
  ): CoroutineDispatcher {
    return if (wolEnabled) {
      newSingleThreadContext("database-writes")
    } else {
      newSingleThreadContext("database-reads-writes")
    }
  }

  @Provides @Singleton @DatabaseReadDispatcher fun provideDatabaseReadDispatcher(
    @WriteAheadLoggingEnabled wolEnabled: Boolean,
    @DatabaseWriteDispatcher databaseWriteDispatcher: CoroutineDispatcher
  ): CoroutineDispatcher {
    return if (wolEnabled) {
      val resources = Resources.getSystem()
      val resId =
        resources.getIdentifier("db_connection_pool_size", "integer", "android")
      val connectionPoolSize = if (resId != 0) {
        resources.getInteger(resId)
      } else {
        2
      }
      newFixedThreadPoolContext(connectionPoolSize, "database-reads")
    } else {
      databaseWriteDispatcher
    }
  }
}
