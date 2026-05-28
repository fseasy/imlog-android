package top.fseasy.imtrace.app.data.repository

import top.fseasy.imtrace.app.domain.model.Message
import top.fseasy.imtrace.app.domain.model.MessageType
import top.fseasy.imtrace.app.domain.model.Statistics
import top.fseasy.imtrace.app.domain.model.Topic
import top.fseasy.imtrace.app.domain.model.TopicMember
import top.fseasy.imtrace.app.domain.model.User
import top.fseasy.imtrace.app.database.Database
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val database: Database,
    private val appPreferences: top.fseasy.imtrace.app.data.datastore.AppPreferencesRepository
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
            id = userId,
            username = username,
            avatarUri = null,
            createdAt = now,
            updatedAt = now
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
class TopicRepository @Inject constructor(
    private val database: Database,
    private val appPreferences: top.fseasy.imtrace.app.data.datastore.AppPreferencesRepository
) {
    fun getTopics(): Flow<List<Topic>> = appPreferences.currentUserId.map { userId ->
        database.topicQueries.getActiveTopics().executeAsList().map { row ->
            val personalState = database.topicQueries
                .getPersonalState(row.id, userId ?: "")
                .executeAsList()
                .firstOrNull()
            Topic(
                id = row.id,
                name = row.name,
                iconUri = row.icon_uri,
                creatorId = row.creator_id,
                createdAt = row.created_at,
                updatedAt = row.updated_at,
                isDeleted = row.is_deleted,
                isPinned = personalState?.pinned ?: false,
                isArchived = personalState?.archived ?: false,
                background = personalState?.background,
                font = database.topicQueries
                    .getDeviceState(row.id)
                    .executeAsList()
                    .firstOrNull()
                    ?.font
            )
        }
    }

    suspend fun createTopic(name: String, creatorId: String): Topic {
        val now = System.currentTimeMillis()
        val topicId = UUID.randomUUID().toString()
        val topic = Topic(
            id = topicId,
            name = name,
            iconUri = null,
            creatorId = creatorId,
            createdAt = now,
            updatedAt = now
        )
        database.topicQueries.insertTopic(
            id = topic.id,
            name = topic.name,
            icon_uri = topic.iconUri,
            creator_id = topic.creatorId,
            created_at = topic.createdAt,
            updated_at = topic.updatedAt,
            is_deleted = false
        )
        database.topicQueries.insertPersonalState(
            topic_id = topicId,
            user_id = creatorId,
            archived = false,
            pinned = false,
            background = null,
            icon = null,
            last_read_at = null,
            updated_at = now
        )
        database.topicQueries.insertDeviceState(
            topic_id = topicId,
            font = null
        )
        return topic
    }

    suspend fun updateTopic(topicId: String, name: String, iconUri: String?) {
        database.topicQueries.updateTopic(
            name = name,
            icon_uri = iconUri,
            updated_at = System.currentTimeMillis(),
            id = topicId
        )
    }

    suspend fun deleteTopic(topicId: String) {
        database.topicQueries.deleteTopicLogical(
            updated_at = System.currentTimeMillis(),
            id = topicId
        )
    }

    suspend fun pinTopic(topicId: String, userId: String, pinned: Boolean) {
        val existing = database.topicQueries
            .getPersonalState(topicId, userId)
            .executeAsList()
            .firstOrNull()
        if (existing != null) {
            database.topicQueries.updatePersonalState(
                archived = existing.archived,
                pinned = pinned,
                background = existing.background,
                icon = existing.icon,
                last_read_at = existing.last_read_at,
                updated_at = System.currentTimeMillis(),
                topic_id = topicId,
                user_id = userId
            )
        }
    }

    suspend fun archiveTopic(topicId: String, userId: String, archived: Boolean) {
        val existing = database.topicQueries
            .getPersonalState(topicId, userId)
            .executeAsList()
            .firstOrNull()
        if (existing != null) {
            database.topicQueries.updatePersonalState(
                archived = archived,
                pinned = existing.pinned,
                background = existing.background,
                icon = existing.icon,
                last_read_at = existing.last_read_at,
                updated_at = System.currentTimeMillis(),
                topic_id = topicId,
                user_id = userId
            )
        }
    }

    suspend fun setTopicFont(topicId: String, font: String?) {
        database.topicQueries.updateDeviceState(
            font = font,
            topic_id = topicId
        )
    }

    suspend fun setTopicBackground(topicId: String, userId: String, background: String?) {
        val existing = database.topicQueries
            .getPersonalState(topicId, userId)
            .executeAsList()
            .firstOrNull()
        if (existing != null) {
            database.topicQueries.updatePersonalState(
                archived = existing.archived,
                pinned = existing.pinned,
                background = background,
                icon = existing.icon,
                last_read_at = existing.last_read_at,
                updated_at = System.currentTimeMillis(),
                topic_id = topicId,
                user_id = userId
            )
        }
    }

    fun getTopic(topicId: String): Flow<Topic?> = appPreferences.currentUserId.map { userId ->
        database.topicQueries.getTopicById(topicId).executeAsList()
            .firstOrNull()?.let { row ->
                val personalState = database.topicQueries
                    .getPersonalState(row.id, userId ?: "")
                    .executeAsList()
                    .firstOrNull()
                Topic(
                    id = row.id,
                    name = row.name,
                    iconUri = row.icon_uri,
                    creatorId = row.creator_id,
                    createdAt = row.created_at,
                    updatedAt = row.updated_at,
                    isDeleted = row.is_deleted,
                    isPinned = personalState?.pinned ?: false,
                    isArchived = personalState?.archived ?: false,
                    background = personalState?.background,
                    font = database.topicQueries
                        .getDeviceState(row.id)
                        .executeAsList()
                        .firstOrNull()
                        ?.font
                )
            }
    }
}

@Singleton
class MessageRepository @Inject constructor(
    private val database: Database,
    private val appPreferences: top.fseasy.imtrace.app.data.datastore.AppPreferencesRepository
) {
    fun getMessages(topicId: String): Flow<List<Message>> = appPreferences.currentUserId.map { _ ->
        database.messageQueries.getMessagesByTopic(topicId).executeAsList().map { row ->
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
