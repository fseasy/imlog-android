package top.fseasy.imlog.data.repository

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import top.fseasy.imlog.data.file.MediaSaveResult
import top.fseasy.imlog.domain.model.Message
import top.fseasy.imlog.domain.model.MessageFactory
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.MessageMediaProcessingStatus
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.Statistics
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.model.toMessageMediaProcessStatus
import top.fseasy.imlog.domain.repository.MessageRepository
import top.fseasy.imlog.sqldelight.Message_media_processing_temp_states
import top.fseasy.imlog.sqldelight.SqlDelightDb
import top.fseasy.imlog.util.retrySQLiteOnKeyConflict
import top.fseasy.imlog.util.toFileProviderUri
import top.fseasy.imlog.worker.MediaFileProcessWorker
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import top.fseasy.imlog.sqldelight.GetMessagesByTopic as GetMessagesByTopicRowEntity
import top.fseasy.imlog.sqldelight.Messages as MessageEntity

@Singleton
class MessageRepositoryImpl @Inject constructor(
    @param:ApplicationContext val context: Context,
    private val database: SqlDelightDb,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val workManager: WorkManager,
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

    override suspend fun sendMediaMessage(
        topicId: TopicId,
        senderId: UserId,
        messageType: MessageType,
        srcMediaUri: Uri,
        deleteSrcMediaWhenSuccess: Boolean,
    ): Unit = withContext(dispatcher) {
        // insert pending message
        val now = System.currentTimeMillis()

        val messageId = retrySQLiteOnKeyConflict {
            val pendingMessage = MessageFactory.createPendingMedia(
                topicId = topicId,
                senderId = senderId,
                type = messageType,
                timestampMs = now,
            )
            val pendingStateEntity =
                Message_media_processing_temp_states(
                    message_id = pendingMessage.id.value,
                    status = MessageMediaProcessingStatus.PROCESSING.value,
                    src_uri = srcMediaUri.toString()
                )
            database.transaction {
                database.messageQueries.insertMessage(pendingMessage.toEntity())
                database.messageQueries.insertMessageMediaProcessingTempStates(pendingStateEntity)
            }
            pendingMessage.id
        }
        // invoke worker to processing media file

    }

    suspend fun sendVoiceMessage(
        topicId: TopicId,
        senderId: UserId,
        cacheVoiceFile: File,
    ): Unit = withContext(dispatcher) {
        val voiceUri = cacheVoiceFile.toFileProviderUri(context)
        sendMediaMessage(
            topicId = topicId,
            senderId = senderId,
            messageType = MessageType.VOICE,
            srcMediaUri = voiceUri,
            deleteSrcMediaWhenSuccess = false
        )
    }

    override suspend fun finishMediaProcessing(
        messageId: MessageId,
        savedMedia: MediaSaveResult.SavedMedia,
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