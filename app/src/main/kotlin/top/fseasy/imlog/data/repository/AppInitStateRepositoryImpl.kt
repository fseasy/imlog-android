package top.fseasy.imlog.data.repository

import android.content.Context
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import top.fseasy.imlog.data.datastore.AppPreferencesRepository
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.features.appinit.AppInitState
import top.fseasy.imlog.sqldelight.SqlDelightDb
import top.fseasy.imlog.sqldelight.App_init_data as AppInitDataEntity

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppInitStateRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: SqlDelightDb,
    private val appPreferences: AppPreferencesRepository,
    private val dispatcher: CoroutineDispatcher,
) {
    fun observeAppInitState(userId: UserId): Flow<AppInitState?> {
        database.appInitStateQueries.selectByUserId(userId.value).asFlow()
            .mapToOneOrNull()
            .mapNotNull { row -> row.toDomain()

            }
    }

    private suspend fun  AppInitStateEntity.toDomain(): AppInitState {
        return AppInitState()
    }
}