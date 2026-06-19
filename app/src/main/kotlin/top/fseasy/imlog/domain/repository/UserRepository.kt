package top.fseasy.imlog.domain.repository

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import top.fseasy.imlog.domain.model.AppInitData
import top.fseasy.imlog.domain.model.AvatarModel
import top.fseasy.imlog.domain.model.User
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.model.UserPreference

interface UserRepository {
    fun observeUserOrNull(): Flow<User?>

    suspend fun createAndSetCurrentUserOrThrow(username: String, avatarModel: AvatarModel): UserId

    suspend fun getLocalSignedInUsers(): List<User>
    suspend fun getUserPreference(userId: UserId): UserPreference?
    fun observeUserAppInitDataOrNull(userId: UserId): Flow<AppInitData?>
    fun observeCurrentUserIdOrNull(): Flow<UserId?>
}