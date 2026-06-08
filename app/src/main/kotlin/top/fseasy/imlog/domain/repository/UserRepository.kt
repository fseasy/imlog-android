package top.fseasy.imlog.domain.repository

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import top.fseasy.imlog.domain.model.User
import top.fseasy.imlog.domain.model.UserId

interface UserRepository {
    val observeUserId: Flow<UserId?>

    fun observeUser(): Flow<User?>

    suspend fun createCurrentUser(username: String, avatarUri: Uri?): User

    suspend fun updateCurrentUser(username: String, avatarUri: String?): Unit
}