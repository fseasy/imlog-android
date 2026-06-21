package top.fseasy.imlog.data.datastore

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import top.fseasy.imlog.data.util.retryOnAnyException
import top.fseasy.imlog.data.util.retrySQLiteOnKeyConflict
import top.fseasy.imlog.domain.model.RetryModel
import top.fseasy.imlog.domain.repository.DbTransactionRunner
import top.fseasy.imlog.sqldelight.SqlDelightDb
import javax.inject.Inject

class SqlDelightTransactionRunner @Inject constructor(
    private val database: SqlDelightDb,
    private val ioDispatcher: CoroutineDispatcher,
) : DbTransactionRunner {
    override suspend fun <T> runInTransaction(
        retry: RetryModel,
        block: () -> T,
    ): T = withContext(ioDispatcher) {
        val transactionBlock: suspend () -> T = {
            database.transactionWithResult {
                block()
            }
        }

        when (retry) {
            RetryModel.None -> {
                transactionBlock()
            }

            RetryModel.OnDbConflict -> {
                retrySQLiteOnKeyConflict {
                    transactionBlock()
                }
            }

            RetryModel.OnAnyException -> {
                retryOnAnyException {
                    transactionBlock()
                }
            }
        }
    }
}