package top.fseasy.imlog.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import top.fseasy.imlog.data.datastore.AppPreferencesRepository
import top.fseasy.imlog.domain.model.User
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.sqldelight.SqlDelightDb
import top.fseasy.imlog.util.retrySQLiteOnKeyConflict
import javax.inject.Inject
import javax.inject.Singleton
import top.fseasy.imlog.sqldelight.Users as UserEntity

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val database: SqlDelightDb,
    private val appPreferences: AppPreferencesRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    val observeUserId: Flow<UserId?> = appPreferences.currentUserId.map { it?.let(::UserId) }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeUser(): Flow<User?> = observeUserId.flatMapLatest { userId ->
        val id = userId?.value ?: return@flatMapLatest flowOf(null)
        database.userQueries.getUserById(id)
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { it?.toDomain() }
            .catch { e ->
                Timber.w(e, "Observe User Get exception")
                emit(null)
            }
    }

    suspend fun createCurrentUser(username: String, avatarUri: String?): User =
        withContext(dispatcher) {
            val now = System.currentTimeMillis()
            val userId = retrySQLiteOnKeyConflict {
                UserId.random()
                    .also { uid ->
                        database.userQueries.insertUser(
                            id = uid.value,
                            username = username,
                            avatar_uri = avatarUri,
                            created_at = now,
                            attributes_updated_at = now
                        )
                    }
            }
            // transaction to make sure currentUserId write done
            try {
                appPreferences.setCurrentUserId(userId.value)
            } catch (e: Exception) {
                Timber.e(e, "Failed to set current user ID in preferences, rolling back DB...")
                runCatching { database.userQueries.deleteUser(userId.value) }
                throw e
            }
            User(
                id = userId,
                username = username,
                avatarUri = avatarUri,
                createdAt = now,
                attributesUpdatedAt = now
            )
        }

    suspend fun updateCurrentUser(username: String, avatarUri: String?) {
        val userId = currentUserId.first() ?: return
        database.userQueries.updateUser(
            username = username,
            avatar_uri = avatarUri,
            updated_at = System.currentTimeMillis(),
            id = userId
        )
    }

    private fun UserEntity.toDomain() = User(
        id = id,
        username = username,
        avatarUri = avatar_uri,
        createdAt = created_at,
        attributesUpdatedAt = updated_at
    )
}