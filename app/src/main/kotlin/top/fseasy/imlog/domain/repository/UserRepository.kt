package top.fseasy.imlog.domain.repository

import kotlinx.coroutines.flow.Flow
import top.fseasy.imlog.domain.model.AppInitData
import top.fseasy.imlog.domain.model.AvatarModel
import top.fseasy.imlog.domain.model.User
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.model.UserPreference

interface UserRepository {
    fun observeCurrentUserIdOrNull(): Flow<UserId?>
    fun observeUserOrNull(): Flow<User?>

    /**
     * @throws Exception when create user failed. parent should process it based on the business logic.
     */
    suspend fun createAndSetCurrentUser(username: String, avatarModel: AvatarModel): UserId

    suspend fun getLocalSignedInUsers(): List<User>
    suspend fun getUserPreference(userId: UserId): UserPreference?

    //---- AppInitData Group
    fun observeUserAppInitDataOrNull(userId: UserId): Flow<AppInitData?>

    /**
     * Needs to run in withContext(IO)
     * */
    fun syncMarkAppInitFirstTopicCreated(userId: UserId): Boolean

    /**
     * Needs to run in withContext(IO)
     * */
    fun syncMarkAppInitWelcomeShown(userId: UserId): Boolean
    //---- ENd of AppInitData Group
}