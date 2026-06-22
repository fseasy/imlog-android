package top.fseasy.imlog.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import top.fseasy.imlog.data.database.createSqlDelightDb
import top.fseasy.imlog.data.datastore.SqlDelightRunner
import top.fseasy.imlog.domain.repository.DbRunner
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

    @Provides
    @Singleton
    fun provideTransactionRunner(
        database: SqlDelightDb,
        dispatcher: CoroutineDispatcher,
    ): DbRunner {
        return SqlDelightRunner(database, dispatcher)
    }
}