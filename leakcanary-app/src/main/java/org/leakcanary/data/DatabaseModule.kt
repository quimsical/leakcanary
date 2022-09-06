package org.leakcanary.data

import android.app.Application
import com.squareup.sqldelight.android.AndroidSqliteDriver
import com.squareup.sqldelight.db.SqlDriver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.leakcanary.Database

@Module
@InstallIn(SingletonComponent::class)
class DatabaseModule {

  @Provides
  @Singleton
  fun provideSqliteDriver(app: Application): SqlDriver = AndroidSqliteDriver(Database.Schema, app, "leakcanary.db")

  @Provides
  @Singleton
  fun provideDatabase(driver: SqlDriver): Database = Database(driver)
}
