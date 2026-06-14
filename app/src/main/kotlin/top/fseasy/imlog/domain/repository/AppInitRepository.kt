package top.fseasy.imlog.domain.repository

import kotlinx.coroutines.flow.Flow
import top.fseasy.imlog.domain.model.AppInitData
import top.fseasy.imlog.domain.model.UserId

interface AppInitRepository {
    fun observeAppInitDataOrNull(userId: UserId): Flow<AppInitData?>
}