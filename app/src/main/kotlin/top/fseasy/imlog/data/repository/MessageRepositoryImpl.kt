package top.fseasy.imlog.data.repository

import android.content.Context
import androidx.core.net.toUri
import androidx.work.WorkManager
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import top.fseasy.imlog.data.util.retryOnAnyException
import top.fseasy.imlog.domain.model.Message
import top.fseasy.imlog.domain.model.MessageFactory
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.MessageFileProcessingStatus
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.Statistics
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.model.toMessageMediaProcessStatus
import top.fseasy.imlog.domain.repository.MessageRepository
import top.fseasy.imlog.sqldelight.Message_file_processing_states as MessageFileProcessingStatesEntity
import top.fseasy.imlog.sqldelight.SqlDelightDb
import top.fseasy.imlog.data.util.retrySQLiteOnKeyConflict
import top.fseasy.imlog.domain.model.MessageFileProcessingErrorType
import top.fseasy.imlog.domain.model.MessageFileProcessingStage
import top.fseasy.imlog.domain.model.UriStr
import top.fseasy.imlog.worker.DeleteFileWorker
import top.fseasy.imlog.worker.MediaFileProcessWorker
import javax.inject.Inject
import javax.inject.Singleton
import top.fseasy.imlog.sqldelight.GetMessagesByTopic as GetMessagesByTopicRowEntity
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

    override suspend fun fileSendingOnInsertingPendingMessage(
        topicId: TopicId,
        senderId: UserId,
        messageType: MessageType,
        srcUriStr: UriStr,
        messageTimestampMs: Long,
    ) = withContext(dispatcher) {
        retryOnAnyException {
            val pendingMessage = MessageFactory.createPendingMedia(
                topicId = topicId,
                senderId = senderId,
                type = messageType,
                timestampMs = messageTimestampMs,
            )
            val pendingStateEntity = createMessageFileProcessingPendingStateValue(
                messageId = pendingMessage.id, srcUriStr = srcUriStr
            )
            database.transaction {
                database.messageQueries.insertMessage(pendingMessage.toEntity())
                database.messageFileProcessingQueries.insertMessageFileProcessingState(
                    pendingStateEntity
                )
            }
            pendingMessage.id
        }
    }

    override suspend fun fileSendingOnSettingInternalCacheFilename(
        messageId: MessageId,
        filename: String?,
    ): Boolean = withContext(dispatcher) {
        retryOnAnyException {
            database.messageFileProcessingQueries.setMessageFileInternalCacheFilename(
                internalCachedFilename = filename, messageId = messageId.value
            ).value > 0L
        }
    }

    override suspend fun fileSendingOnSettingProcessingStatus(
        messageId: MessageId,
        status: MessageFileProcessingStatus,
        stage: MessageFileProcessingStage,
        errorType: MessageFileProcessingErrorType,
        errorUserRetriable: Boolean,
    ) = withContext(dispatcher) {
        retryOnAnyException {
            database.messageFileProcessingQueries.setMessageFileProcessingStatus(
                status = status.value,
                currentStage = stage.value,
                errorType = errorType.value,
                errorUserRetriable = if (errorUserRetriable) 1L else 0L,
                messageId = messageId.value
            ).value > 0L
        }
    }

    override suspend fun sendMediaMessage(
        topicId: TopicId,
        senderId: UserId,
        messageType: MessageType,
        srcMediaCopySource: MessageMediaCopySource,
    ): Unit = withContext(dispatcher) {
        // insert pending message
        val now = System.currentTimeMillis()
        val srcMediaUri = when (srcMediaCopySource) {
            is MessageMediaCopySource.FromUri -> srcMediaCopySource.uri
            is MessageMediaCopySource.FromFile -> srcMediaCopySource.file.toUri()
        }

        val messageId = retrySQLiteOnKeyConflict {
            val pendingMessage = MessageFactory.createPendingMedia(
                topicId = topicId,
                senderId = senderId,
                type = messageType,
                timestampMs = now,
            )
            val pendingStateEntity = Message_media_processing_temp_states(
                message_id = pendingMessage.id.value,
                status = MessageFileProcessingStatus.Processing.value,
                src_uri = srcMediaUri.toString()
            )
            database.transaction {
                database.messageQueries.insertMessage(pendingMessage.toEntity())
                database.messageQueries.insertMessageMediaProcessingTempStates(pendingStateEntity)
            }
            pendingMessage.id
        }
        // invoke worker to processing media file
        val mediaProcessRequest = MediaFileProcessWorker.createRequest(
            messageId = messageId,
            topicId = topicId,
            senderId = senderId,
            messageTimestampMs = now,
            srcMediaUri = srcMediaUri
        )
        if (srcMediaCopySource is MessageMediaCopySource.FromFile && srcMediaCopySource.deleteOnCopySuccess) {
            val deleteRequest = DeleteFileWorker.createRequest(srcMediaCopySource.file.toString())
            WorkManager.getInstance(context)
                .beginWith(mediaProcessRequest)
                .then(deleteRequest)
                .enqueue()
        } else {
            WorkManager.getInstance(context)
                .enqueue(mediaProcessRequest)
        }
    }

    override suspend fun finishMediaProcessing(
        messageId: MessageId,
        savedMedia: SavedMedia,
    ) {
        database.transaction {
            database.messageQueries.updateMessageMediaFields(
                filename = savedMedia.filename,
                original_filename = savedMedia.originalFilename,
                file_size = savedMedia.fileSize,
                thumbnail_name = savedMedia.thumbnailFilename,
                mime_type = savedMedia.mimeType,
                duration = savedMedia.duration,
                width = savedMedia.width?.toLong(),
                height = savedMedia.height?.toLong(),
                message_id = messageId.value
            )
            database.messageQueries.deleteMessageMediaProcessingTempStates(messageId.value)
        }
    }

    private fun createMessageFileProcessingPendingStateValue(
        messageId: MessageId,
        srcUriStr: UriStr,
    ) = MessageFileProcessingStatesEntity(
        message_id = messageId.value,
        status = MessageFileProcessingStatus.Pending.value,
        current_stage = null,
        error_type = null,
        error_user_retriable = null,
        src_uri = srcUriStr.value,
        internal_cached_filename = null
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
            filename = filename,
            fileSize = file_size,
            mimeType = mime_type,
            duration = duration,
            width = width?.toInt(),
            height = height?.toInt(),
            thumbnailName = thumbnail_name,
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
        filename = filename,
        file_size = fileSize,
        duration = duration,
        thumbnail_name = thumbnailName,
        created_at = createdAt,
        attributes_updated_at = attributesUpdatedAt,
        is_deleted = 0,
        original_filename = originalFilename,
        mime_type = mimeType,
        width = width?.toLong(),
        height = height?.toLong()
    )
}