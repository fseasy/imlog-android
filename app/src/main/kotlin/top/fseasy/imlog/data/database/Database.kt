package top.fseasy.imlog.data.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import top.fseasy.imlog.sqldelight.SqlDelightDb

/**
 * It will be provided as singleton by Hilt Singleton Binds. see `di.DatabseModel`
 */
fun createSqlDelightDb(context: Context): SqlDelightDb {
    val driver = AndroidSqliteDriver(
        schema = SqlDelightDb.Schema,
        context = context,
        name = "app.db",
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
