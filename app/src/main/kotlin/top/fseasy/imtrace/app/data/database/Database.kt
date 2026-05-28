package top.fseasy.imtrace.app.data.database

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import top.fseasy.imtrace.app.database.Database
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): Database {
        val driver = AndroidSqliteDriver(
            schema = Database.Schema,
            context = context,
            name = "imtrace.db"
        )
        return Database(driver)
    }
}
