package top.fseasy.imlog.data.repository

import android.content.Context
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import timber.log.Timber
import top.fseasy.imlog.data.datastore.AppPreferencesRepository
import top.fseasy.imlog.domain.model.AppInitData
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.AppInitRepository
import top.fseasy.imlog.sqldelight.SqlDelightDb
import top.fseasy.imlog.sqldelight.App_init_data as AppInitDataEntity

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppInitRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: SqlDelightDb,
    private val appPreferences: AppPreferencesRepository,
    private val dispatcher: CoroutineDispatcher,
) : AppInitRepository {
    override fun observeAppInitDataOrNull(userId: UserId): Flow<AppInitData?> {
        return database.appInitDataQueries.selectByUserId(userId.value)
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { row -> row?.toDomain() }
            .catch { e ->
                Timber.e(e, "Failed to observe AppInit data, just emit null")
                emit(null)
            }
    }

    private suspend fun AppInitDataEntity.toDomain(): AppInitData {
        return AppInitData(
            userId = UserId(user_id),
            storageUriSelected = storage_uri_selected == 1L,
            firstTopicCreated = first_topic_created == 1L,
            WelcomeShown = welcome_shown == 1L,
        )
    }
}