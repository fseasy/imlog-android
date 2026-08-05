package top.fseasy.imlog.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import top.fseasy.imlog.data.util.retrySQLiteOnKeyConflict
import top.fseasy.imlog.di.ApplicationIoScope
import top.fseasy.imlog.domain.model.AppInitData
import top.fseasy.imlog.domain.model.AuthState
import top.fseasy.imlog.domain.model.User
import top.fseasy.imlog.domain.model.UserAvatarModel
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.model.UserPreference
import top.fseasy.imlog.domain.repository.AppStateRepository
import top.fseasy.imlog.domain.repository.UserRepository
import top.fseasy.imlog.sqldelight.SqlDelightDb
import javax.inject.Inject
import javax.inject.Singleton
import top.fseasy.imlog.sqldelight.App_init_data as AppInitDataEntity
import top.fseasy.imlog.sqldelight.User_preference as UserPreferenceEntity
import top.fseasy.imlog.sqldelight.Users as UserEntity

@Singleton
class UserRepositoryImpl
@Inject
constructor(
    private val database: SqlDelightDb,
    private val appStateRepository: AppStateRepository,
    private val dispatcher: CoroutineDispatcher,
    @ApplicationIoScope private val applicationIoScope: CoroutineScope,
) : UserRepository {

  override val authState: StateFlow<AuthState> =
      observeCurrentUserIdOrNull()
          .map { uid ->
            if (uid == null) AuthState.Unauthenticated else AuthState.Authenticated(userId = uid)
          }
          .stateIn(
              scope = applicationIoScope,
              started = SharingStarted.WhileSubscribed(5_000),
              initialValue = AuthState.Loading,
          )

  override fun observeCurrentUserIdOrNull(): Flow<UserId?> =
      appStateRepository.observeCurrentUserIdOrNull()

  /** no exception will throw */
  @OptIn(ExperimentalCoroutinesApi::class)
  override fun observeUserOrNull(): Flow<User?> =
      safeObserveFlowOrNull({ "Observe User Get exception" }) {
        appStateRepository.observeCurrentUserIdOrNull().flatMapLatest { userId ->
          val id = userId ?: return@flatMapLatest flowOf(null)
          database.userQueries.getUserById(id).asFlow().mapToOneOrNull(dispatcher).map {
            it?.toDomain()
          }
        }
      }

  override fun observeUserAppInitDataOrNull(userId: UserId): Flow<AppInitData?> =
      safeObserveFlowOrNull({ "Failed to observe AppInit data" }) {
        database.appInitDataQueries
            .selectByUserId(userId)
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { row -> row?.toDomain() }
            .distinctUntilChanged()
      }

  /** SYNC fun. expected to be used in withContext(IO) */
  override fun syncMarkAppInitFirstTopicCreated(userId: UserId): Boolean {
    val affectedLine = database.appInitDataQueries.markFirstTopicCreated(userId).value
    return affectedLine > 0L
  }

  /** SYNC fun. expected to be used in withContext(IO) */
  override fun syncMarkAppInitWelcomeShown(userId: UserId): Boolean {
    val affectedLine = database.appInitDataQueries.markWelcomeShown(userId).value
    return affectedLine > 0L
  }

  /** @throws android.database.sqlite.SQLiteException */
  override suspend fun getLocalSignedInUsers(): List<User> =
      withContext(dispatcher) {
        database.userQueries.getLocalSignedInUsers().executeAsList().map { it.toDomain() }
      }

  override suspend fun createAndSetCurrentUser(
      username: String,
      avatarModel: UserAvatarModel,
  ): UserId =
      withContext(dispatcher) {
        val userId = retrySQLiteOnKeyConflict {
          UserId.random().also { uid ->
            database.transaction {
              syncInsertNewUserIntoUserTable(
                  userId = uid,
                  username = username,
                  avatarModel = avatarModel,
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
        database.userPreferenceQueries.getByUserId(userId).executeAsOneOrNull()?.toDomain()
      }

  // === END of User Preference

  private fun UserEntity.toDomain() =
      User(
          id = id,
          username = username,
          avatarModel = avatar_model,
          lastSignInAt = last_signin_at,
          createdAt = created_at,
          attributesUpdatedAt = attributes_updated_at,
      )

  private fun UserPreferenceEntity.toDomain(): UserPreference =
      UserPreference(
          userId = user_id,
          mediaStorageRootUri = shared_storage_root_uri,
          themeMode = theme_mode,
      )

  private fun AppInitDataEntity.toDomain(): AppInitData {
    return AppInitData(
        userId = user_id,
        storageUriSelected = storage_uri_selected,
        firstTopicCreated = first_topic_created,
        welcomeShown = welcome_shown,
    )
  }

  private fun syncInsertNewUserIntoUserTable(
      userId: UserId,
      username: String,
      avatarModel: UserAvatarModel,
      now: Long = System.currentTimeMillis(),
  ) {
    database.userQueries.insertUser(
        UserEntity(
            id = userId,
            username = username,
            avatar_model = avatarModel,
            last_signin_at = now,
            created_at = now,
            attributes_updated_at = now,
        )
    )
  }

  private fun syncInsertNewUserIntoAppInitDataTable(userId: UserId) {
    database.appInitDataQueries.insert(
        AppInitDataEntity(
            user_id = userId,
            storage_uri_selected = false,
            first_topic_created = false,
            welcome_shown = false,
        )
    )
  }
}
