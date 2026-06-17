package top.fseasy.imlog.domain.repository

import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus
import top.fseasy.imlog.domain.model.UserId

interface AppStateRepository {
    @ApiStatus.Internal
    fun observeCurrentUserIdOrNull(): Flow<UserId?>
    @ApiStatus.Internal
    suspend fun setCurrentId(userId: UserId)
    @ApiStatus.Internal
    fun syncSetCurrentId(userId: UserId)
}