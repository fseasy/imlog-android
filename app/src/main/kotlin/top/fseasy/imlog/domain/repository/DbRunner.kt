package top.fseasy.imlog.domain.repository

import top.fseasy.imlog.domain.model.RetryModel


interface DbRunner {
    /**
     * It will wrap all the db operation (within the block param) to a
     * 1. db.transaction
     * 2. withContext(IO)
     * 3. retry block (depends on the RetryModel)
     * @param block it should be a serials of sync operations (no suspend allowed!)
     */
    suspend fun <T> runInTransaction(retry: RetryModel = RetryModel.None, block: () -> T): T

    /**
     * It will wrap all the db operation (within the block param) to a
     * 1. withContext(IO)
     * 2. retry block (depends on the RetryModel)
     * @param block it should be a serials of sync operations (no suspend allowed!)
     */
    suspend fun <T> runInIoThread(retry: RetryModel = RetryModel.None, block: () -> T): T
}