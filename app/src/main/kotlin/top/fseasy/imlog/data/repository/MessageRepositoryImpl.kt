package top.fseasy.imlog.data.repository

import android.content.Context
import androidx.core.net.toUri
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import top.fseasy.imlog.data.util.retryOnAnyException
import top.fseasy.imlog.domain.model.FileMetadataUnion
import top.fseasy.imlog.domain.model.Message
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.MessageProcessingErrorStage
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.Statistics
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.model.toMessageMediaProcessStatus
import top.fseasy.imlog.domain.repository.MessageFileSource
import top.fseasy.imlog.domain.repository.MessageRepository
import top.fseasy.imlog.sqldelight.SqlDelightDb
import javax.inject.Inject
import javax.inject.Singleton
import top.fseasy.imlog.sqldelight.GetMessagesByTopic as GetMessagesByTopicRowEntity
import top.fseasy.imlog.sqldelight.Message_file_processing_task_states as FileProcessingTaskStateEntity
import top.fseasy.imlog.sqldelight.Messages as MessageEntity

@Singleton
class MessageRepositoryImpl @Inject constructor(
    @param:ApplicationContext val context: Context,
    private val database: SqlDelightDb,
    private val dispatcher: CoroutineDispatcher,
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

    override fun syncInsertInitialFileMessage(
        topicId: TopicId,
        senderId: UserId,
        type: MessageType,
        timestampMs: Long,
        fileMetadata: FileMetadataUnion,
    ): MessageId {
        val initialMessage = createInitialFileMessageEntity(
            topicId = topicId,
            senderId = senderId,
            type = type,
            timestampMs = timestampMs,
            srcMetadata = fileMetadata
        )
        database.messageQueries.insertMessage(initialMessage)
        return MessageId(initialMessage.id)
    }

    override fun syncInsertInitialFileProcessingTaskState(
        messageId: MessageId,
        fileSource: MessageFileSource,
        taskStartTime: Long,
    ) {
        val initialStateEntity = createInitialFileProcessingTaskStateEntity(
            messageId = messageId,
            fileSource = fileSource,
            taskStartTime = taskStartTime
        )

        database.messageFileProcessingQueries.insertMessageFileProcessingState(
            initialStateEntity
        )
    }

    /**
     * insert message to DB. only suitable for Text message because the other message need extra file process.
     * TODO: remove this when we also need some side effects when processing text message
     */
    override suspend fun saveTextMessage(message: Message): Unit = withContext(dispatcher) {
        database.messageQueries.insertMessage(message.toEntity())
    }

    override suspend fun delete(messageId: MessageId): Boolean = withContext(dispatcher) {
        database.messageQueries.deleteMessageLogical(id = messageId.value).value > 0L
    }

    override suspend fun setFileProcessingInternalCacheFilename(
        messageId: MessageId,
        filename: String?,
    ): Boolean = withContext(dispatcher) {
        retryOnAnyException {
            database.messageFileProcessingQueries.setMessageFileInternalCacheFilename(
                internalCachedFilename = filename, messageId = messageId.value
            ).value > 0L
        }
    }

    override suspend fun setFileMessageRawFilename(
        messageId: MessageId,
        filename: String?,
    ): Boolean = withContext(dispatcher) {
        retryOnAnyException {
            database.messageQueries.updateMessageRawFilename(
                filename = filename, messageId = messageId.value
            ).value > 0L
        }
    }

    override suspend fun setFileMessageThumbnailFilename(
        messageId: MessageId,
        filename: String?,
    ): Boolean = withContext(dispatcher) {
        retryOnAnyException {
            database.messageQueries.updateMessageThumbnailFilename(
                filename = filename, messageId = messageId.value
            ).value > 0L
        }
    }


    override suspend fun setFileProcessingTaskFail(
        messageId: MessageId,
        stage: MessageProcessingErrorStage,
        errorUserRetryable: Boolean,
    ) = withContext(dispatcher) {
        retryOnAnyException {
            database.messageFileProcessingQueries.updateMessageFileProcessingError(
                errorStage = stage.value,
                errorUserRetryable = if (errorUserRetryable) 1L else 0L,
                messageId = messageId.value
            ).value > 0L
        }
    }

    override suspend fun deleteFileProcessingTaskState(
        messageId: MessageId,
    ) = withContext(dispatcher) {
        retryOnAnyException {
            database.messageFileProcessingQueries.deleteMessageFileProcessingState(messageId.value).value > 0L
        }
    }


    private fun createInitialFileProcessingTaskStateEntity(
        messageId: MessageId,
        fileSource: MessageFileSource,
        taskStartTime: Long,
    ): FileProcessingTaskStateEntity {
        val (srcUriDbStr, internalCacheFilename) = when (fileSource) {
            is MessageFileSource.FromUriStr -> fileSource.uriStr.value to null
            is MessageFileSource.FromMessageCacheFile -> null to fileSource.filename
        }
        return FileProcessingTaskStateEntity(
            message_id = messageId.value,
            src_uri = srcUriDbStr,
            internal_cached_filename = internalCacheFilename,
            task_tart_time = taskStartTime,
            error_stage = null,
            error_user_retryable = null,
        )
    }

    private fun createInitialFileMessageEntity(
        topicId: TopicId,
        senderId: UserId,
        type: MessageType,
        timestampMs: Long,
        srcMetadata: FileMetadataUnion,
    ) = MessageEntity(
        id = MessageId.random().value,
        topic_id = topicId.value,
        sender_id = senderId.value,
        type = type.value,
        content = null,
        created_at = timestampMs,
        mime_type = srcMetadata.mimeType,
        width = srcMetadata.width?.toLong(),
        height = srcMetadata.height?.toLong(),
        duration = srcMetadata.duration,
        file_size = srcMetadata.fileSize,
        original_filename = srcMetadata.displayName,
        raw_filename = null,
        thumbnail_filename = null,
        attributes_updated_at = timestampMs,
        is_deleted = 0
    )

    private fun GetMessagesByTopicRowEntity.toDomain(currentUserId: UserId): Message {
        return Message(
            id = MessageId(id),
            topicId = TopicId(topic_id),
            senderId = UserId(sender_id),
            type = MessageType.fromValue(type),
            content = content,
            // media file fields
            originalFileUri = src_uri?.toUri(),
            fileProcessStatus = status?.toMessageMediaProcessStatus(),
            originalFilename = original_filename,
            filename = raw_filename,
            fileSize = file_size,
            mimeType = mime_type,
            duration = duration,
            width = width?.toInt(),
            height = height?.toInt(),
            thumbnailName = thumbnail_filename,
            // - end of media file fields
            createdAt = created_at,
            attributesUpdatedAt = attributes_updated_at,

            )
    }

    private fun Message.toEntity() = MessageEntity(
        id = id.value,
        topic_id = topicId.value,
        sender_id = senderId.value,
        /**
         * should only occur in the following condition:
         * 1. Entity -> Domain (get invalid type, type = null)
         * 2. Domain -> Entity (set the null type to `__unknown__`)
         */
        type = type?.value ?: "__unknown__",
        content = content,
        raw_filename = filename,
        file_size = fileSize,
        duration = duration,
        thumbnail_filename = thumbnailName,
        created_at = createdAt,
        attributes_updated_at = attributesUpdatedAt,
        is_deleted = 0,
        original_filename = originalFilename,
        mime_type = mimeType,
        width = width?.toLong(),
        height = height?.toLong()
    )
}