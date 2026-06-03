package top.fseasy.imlog.data.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import top.fseasy.imlog.sqldelight.SqlDelightDb
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
    fun provideDatabase(@ApplicationContext context: Context): SqlDelightDb {
        val driver = AndroidSqliteDriver(
            schema = SqlDelightDb.Schema,
            context = context,
            name = "imlog.db",
            // Enable foreign_keys to enable cascade delete
            callback = object : AndroidSqliteDriver.Callback(SqlDelightDb.Schema) {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    // Enable foreign keys
                    db.setForeignKeyConstraintsEnabled(true)
                }
            }
        )
        return SqlDelightDb(driver)
    }
}
