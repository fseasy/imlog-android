package top.fseasy.imlog.data.datastore

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import top.fseasy.imlog.data.util.retryOnAnyException
import top.fseasy.imlog.data.util.retrySQLiteOnKeyConflict
import top.fseasy.imlog.domain.model.RetryModel
import top.fseasy.imlog.domain.repository.DbRunner
import top.fseasy.imlog.sqldelight.SqlDelightDb
import javax.inject.Inject

class SqlDelightRunner @Inject constructor(
    private val database: SqlDelightDb,
    private val ioDispatcher: CoroutineDispatcher,
) : DbRunner {
    override suspend fun <T> runInTransaction(
        retry: RetryModel,
        block: () -> T,
    ): T = runInIoThread(retry, block = {
        database.transactionWithResult {
            block()
        }
    })

    override suspend fun <T> runInIoThread(retry: RetryModel, block: () -> T): T =
        withContext(ioDispatcher) {
            when (retry) {
                RetryModel.None -> {
                    block()
                }

                RetryModel.OnDbConflict -> {
                    retrySQLiteOnKeyConflict {
                        block()
                    }
                }

                RetryModel.OnAnyException -> {
                    retryOnAnyException {
                        block()
                    }
                }
            }
        }
}