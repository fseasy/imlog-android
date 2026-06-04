package top.fseasy.imlog.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import top.fseasy.imlog.data.file.toMessageAbsolutePath
import top.fseasy.imlog.domain.model.Message
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.Statistics
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.MessageRepository
import top.fseasy.imlog.sqldelight.SqlDelightDb
import top.fseasy.imlog.util.pathNameBySubstring
import javax.inject.Inject
import javax.inject.Singleton
import top.fseasy.imlog.sqldelight.Messages as MessageEntity

@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val database: SqlDelightDb,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MessageRepository {

    /**
     * To render the timeline message list.
     * @param currentUserId: used to generate the full path of media resources
     */
    override fun observeTopicMessages(
        topicId: TopicId,
        currentUserId: UserId,
    ): Flow<List<Message>> = database.messageQueries.getMessagesByTopic(topicId.value)
        .asFlow()
        .mapToList(dispatcher)
        .map { rows -> rows.map { it.toDomain(currentUserId) } }

    override fun observeStatistics(senderId: UserId): Flow<Statistics> =
        database.messageStatQueries.statOneUserUsage(senderId.value)
            .asFlow()
            .mapToOne(dispatcher)
            .map { Statistics(totalDays = it.total_days, totalMessages = it.total_messages) }

    override suspend fun save(message: Message): Unit = withContext(dispatcher) {
        database.messageQueries.insertMessage(message.toEntity())
    }

    override suspend fun delete(messageId: MessageId): Boolean = withContext(dispatcher) {
        database.messageQueries.deleteMessageLogical(id = messageId.value).value > 0L
    }

    private fun MessageEntity.toDomain(currentUserId: UserId): Message {
        return Message(
            id = MessageId(id),
            topicId = TopicId(topic_id),
            senderId = UserId(sender_id),
            type = MessageType.fromValue(type),
            content = content,
            filePath = filename.toMessageAbsolutePath(currentUserId.value, created_at),
            fileSize = file_size,
            duration = duration,
            thumbnailPath = thumbnail_name.toMessageAbsolutePath(
                currentUserId.value, created_at
            ),
            createdAt = created_at,
            attributesUpdatedAt = attributes_updated_at,
        )
    }

    private fun Message.toEntity() = MessageEntity(
        id = id.value,
        topic_id = topicId.value,
        sender_id = senderId.value,
        /**
         * should only occur in the logic:
         * 1. Entity -> Domain (get invalid type, type = null)
         * 2. Domain -> Entity (set the null type to `__unknown__`)
         */
        type = type?.value ?: "__unknown__",
        content = content,
        filename = filePath.pathNameBySubstring(),
        file_size = fileSize,
        duration = duration,
        thumbnail_name = thumbnailPath.pathNameBySubstring(),
        created_at = createdAt,
        attributes_updated_at = attributesUpdatedAt,
        is_deleted = 0
    )
}