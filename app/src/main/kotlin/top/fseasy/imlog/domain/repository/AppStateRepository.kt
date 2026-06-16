package top.fseasy.imlog.domain.repository

import kotlinx.coroutines.flow.Flow
import top.fseasy.imlog.domain.model.UserId

interface AppStateRepository {
    fun observeCurrentUserId(): Flow<UserId?>
    suspend fun setCurrentId(userId: UserId)
}