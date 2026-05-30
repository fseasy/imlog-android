package top.fseasy.imlog.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import top.fseasy.imlog.domain.model.Message
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.Statistics
import top.fseasy.imlog.domain.model.User
import top.fseasy.imlog.sqldelight.SqlDelightDb
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val database: SqlDelightDb,
    private val appPreferences: top.fseasy.imlog.data.datastore.AppPreferencesRepository
) {
    val currentUserId: Flow<String?> = appPreferences.currentUserId

    fun getCurrentUser(): Flow<User?> = currentUserId.map { userId ->
        userId?.let { id ->
            database.userQueries.getUserById(id).executeAsList().firstOrNull()?.let { row ->
                User(
                    id = row.id,
                    username = row.username,
                    avatarUri = row.avatar_uri,
                    createdAt = row.created_at,
                    updatedAt = row.updated_at
                )
            }
        }
    }

    suspend fun createCurrentUser(username: String): User {
        val now = System.currentTimeMillis()
        val userId = UUID.randomUUID().toString()
        val user = User(
            id = userId, username = username, avatarUri = null, createdAt = now, updatedAt = now
        )
        database.userQueries.insertUser(
            id = user.id,
            username = user.username,
            avatar_uri = user.avatarUri,
            created_at = user.createdAt,
            updated_at = user.updatedAt
        )
        appPreferences.setCurrentUserId(userId)
        return user
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
}

@Singleton
class MessageRepository @Inject constructor(
    private val database: SqlDelightDb,
    private val appPreferences: top.fseasy.imlog.data.datastore.AppPreferencesRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    fun getMessages(topicId: String): Flow<List<Message>> =
        database.messageQueries.getMessagesByTopic(topicId).asFlow().mapToList(dispatcher)
            .map { rows ->
                rows.map { row ->
                    Message(
                        id = row.id,
                        topicId = row.topic_id,
                        senderId = row.sender_id,
                        type = MessageType.valueOf(row.type.uppercase()),
                        content = row.content,
                        filePath = row.file_path,
                        fileSize = row.file_size,
                        duration = row.duration,
                        thumbnailPath = row.thumbnail_path,
                        createdAt = row.created_at,
                        updatedAt = row.updated_at,
                        isDeleted = row.is_deleted
                    )
                }
            }

    fun getAllMessages(): Flow<List<Message>> = appPreferences.currentUserId.map { _ ->
        database.messageQueries.getAllActiveMessages().executeAsList().map { row ->
            Message(
                id = row.id,
                topicId = row.topic_id,
                senderId = row.sender_id,
                type = MessageType.valueOf(row.type.uppercase()),
                content = row.content,
                filePath = row.file_path,
                fileSize = row.file_size,
                duration = row.duration,
                thumbnailPath = row.thumbnail_path,
                createdAt = row.created_at,
                updatedAt = row.updated_at,
                isDeleted = row.is_deleted
            )
        }
    }

    fun getStatistics(): Flow<Statistics> = getAllMessages().map { messages ->
        if (messages.isEmpty()) {
            Statistics(totalDays = 0, totalMessages = 0)
        } else {
            val minDate = messages.minOf { it.createdAt }
            val maxDate = messages.maxOf { it.createdAt }
            val daysDiff = ((maxDate - minDate) / (24 * 60 * 60 * 1000)).toInt() + 1
            Statistics(totalDays = daysDiff, totalMessages = messages.size)
        }
    }

    suspend fun sendTextMessage(topicId: String, senderId: String, content: String): Message {
        val now = System.currentTimeMillis()
        val message = Message(
            id = UUID.randomUUID().toString(),
            topicId = topicId,
            senderId = senderId,
            type = MessageType.TEXT,
            content = content,
            filePath = null,
            fileSize = null,
            duration = null,
            thumbnailPath = null,
            createdAt = now,
            updatedAt = null,
            isDeleted = false
        )
        database.messageQueries.insertMessage(
            id = message.id,
            topic_id = message.topicId,
            sender_id = message.senderId,
            type = message.type.name.lowercase(),
            content = message.content,
            file_path = message.filePath,
            file_size = message.fileSize,
            duration = message.duration,
            thumbnail_path = message.thumbnailPath,
            created_at = message.createdAt,
            updated_at = message.updatedAt,
            is_deleted = message.isDeleted
        )
        return message
    }

    suspend fun sendMediaMessage(
        topicId: String,
        senderId: String,
        type: MessageType,
        filePath: String,
        fileSize: Long,
        duration: Long?,
        thumbnailPath: String?
    ): Message {
        val now = System.currentTimeMillis()
        val message = Message(
            id = UUID.randomUUID().toString(),
            topicId = topicId,
            senderId = senderId,
            type = type,
            content = null,
            filePath = filePath,
            fileSize = fileSize,
            duration = duration,
            thumbnailPath = thumbnailPath,
            createdAt = now,
            updatedAt = null,
            isDeleted = false
        )
        database.messageQueries.insertMessage(
            id = message.id,
            topic_id = message.topicId,
            sender_id = message.senderId,
            type = message.type.name.lowercase(),
            content = message.content,
            file_path = message.filePath,
            file_size = message.fileSize,
            duration = message.duration,
            thumbnail_path = message.thumbnailPath,
            created_at = message.createdAt,
            updated_at = message.updatedAt,
            is_deleted = message.isDeleted
        )
        return message
    }

    suspend fun deleteMessage(messageId: String) {
        database.messageQueries.deleteMessageLogical(
            id = messageId
        )
    }
}
