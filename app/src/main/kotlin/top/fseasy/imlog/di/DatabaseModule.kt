package top.fseasy.imlog.di

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import top.fseasy.imlog.data.database.createSqlDelightDb
import top.fseasy.imlog.sqldelight.SqlDelightDb
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideSqlDelightDb(@ApplicationContext context: Context): SqlDelightDb {
        return createSqlDelightDb(context)
    }
}