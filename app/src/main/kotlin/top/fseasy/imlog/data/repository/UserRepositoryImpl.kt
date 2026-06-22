package top.fseasy.imlog.data.repository

import androidx.core.net.toUri
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import top.fseasy.imlog.domain.model.AppInitData
import top.fseasy.imlog.domain.model.AvatarModel
import top.fseasy.imlog.domain.model.User
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.model.UserPreference
import top.fseasy.imlog.domain.model.serialize
import top.fseasy.imlog.domain.repository.AppStateRepository
import top.fseasy.imlog.domain.repository.UserRepository
import top.fseasy.imlog.sqldelight.SqlDelightDb
import top.fseasy.imlog.data.util.retrySQLiteOnKeyConflict
import top.fseasy.imlog.domain.model.defaultUserPresetAvatar
import top.fseasy.imlog.domain.model.toAvatarModelOrNull
import javax.inject.Inject
import javax.inject.Singleton
import top.fseasy.imlog.sqldelight.App_init_data as AppInitDataEntity
import top.fseasy.imlog.sqldelight.User_preference as UserPreferenceEntity
import top.fseasy.imlog.sqldelight.Users as UserEntity

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val database: SqlDelightDb,
    private val appStateRepository: AppStateRepository,
    private val dispatcher: CoroutineDispatcher,
) : UserRepository {

    override fun observeCurrentUserIdOrNull(): Flow<UserId?> =
        appStateRepository.observeCurrentUserIdOrNull()

    /**
     * no exception will throw
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeUserOrNull(): Flow<User?> = appStateRepository.observeCurrentUserIdOrNull()
        .flatMapLatest { userId ->
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

    override fun observeUserAppInitDataOrNull(userId: UserId): Flow<AppInitData?> {
        return database.appInitDataQueries.selectByUserId(userId.value)
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { row -> row?.toDomain() }
            .catch { e ->
                Timber.e(e, "Failed to observe AppInit data, just emit null")
                emit(null)
            }
            .distinctUntilChanged()
    }

    /**
     * SYNC fun. expected to be used in withContext(IO)
     */
    override fun syncMarkAppInitFirstTopicCreated(userId: UserId): Boolean {
        val affectedLine = database.appInitDataQueries.markFirstTopicCreated(userId.value).value
        return affectedLine > 0L
    }

    /**
     * SYNC fun. expected to be used in withContext(IO)
     */
    override fun syncMarkAppInitWelcomeShown(userId: UserId): Boolean {
        val affectedLine = database.appInitDataQueries.markWelcomeShown(userId.value).value
        return affectedLine > 0L
    }

    /**
     * @throws android.database.sqlite.SQLiteException
     */
    override suspend fun getLocalSignedInUsers(): List<User> = withContext(dispatcher) {
        database.userQueries.getLocalSignedInUsers()
            .executeAsList()
            .map { it.toDomain() }
    }

    override suspend fun createAndSetCurrentUser(
        username: String,
        avatarModel: AvatarModel,
    ): UserId = withContext(dispatcher) {
        val userId = retrySQLiteOnKeyConflict {
            UserId.random()
                .also { uid ->
                    database.transaction {
                        syncInsertNewUserIntoUserTable(
                            userId = uid, username = username, avatarModel = avatarModel
                        )
                        syncInsertNewUserIntoAppInitDataTable(uid)
                        appStateRepository.syncSetCurrentId(uid)
                    }
                }
        }
        userId
    }

    // === User Preference data
    override suspend fun getUserPreference(userId: UserId): UserPreference? =
        withContext(dispatcher) {
            database.userPreferenceQueries.getByUserId(userId.value)
                .executeAsOneOrNull()
                ?.toDomain()
        }
    // === END of User Preference

    private fun UserEntity.toDomain() = User(
        id = UserId(id),
        username = username,
        avatarModel = avatar_model.toAvatarModelOrNull() ?: defaultUserPresetAvatar(),
        lastSignInAt = last_signin_at,
        createdAt = created_at,
        attributesUpdatedAt = attributes_updated_at,
    )

    private fun UserPreferenceEntity.toDomain(): UserPreference = UserPreference(
        userId = UserId(user_id),
        mediaStorageRootUri = shared_storage_root_uri?.toUri(),
        themeMode = theme_mode
    )

    private fun AppInitDataEntity.toDomain(): AppInitData {
        return AppInitData(
            userId = UserId(user_id),
            storageUriSelected = storage_uri_selected == 1L,
            firstTopicCreated = first_topic_created == 1L,
            WelcomeShown = welcome_shown == 1L,
        )
    }

    private fun syncInsertNewUserIntoUserTable(
        userId: UserId,
        username: String,
        avatarModel: AvatarModel,
        now: Long = System.currentTimeMillis(),
    ) {
        database.userQueries.insertUser(
            UserEntity(
                id = userId.value,
                username = username,
                avatar_model = avatarModel.serialize(),
                last_signin_at = now,
                created_at = now,
                attributes_updated_at = now
            )
        )
    }

    private fun syncInsertNewUserIntoAppInitDataTable(userId: UserId) {
        database.appInitDataQueries.insert(
            AppInitDataEntity(
                user_id = userId.value,
                storage_uri_selected = 0,
                first_topic_created = 0,
                welcome_shown = 0
            )
        )
    }
}