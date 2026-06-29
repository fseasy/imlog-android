package top.fseasy.imlog.domain.repository

import kotlinx.coroutines.flow.Flow
import top.fseasy.imlog.domain.model.MediaMetadataUnion
import top.fseasy.imlog.domain.model.Message
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.MessageMediaCopySource
import top.fseasy.imlog.domain.model.MessageProcessingErrorStage
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.Statistics
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UriStr
import top.fseasy.imlog.domain.model.UserId

interface MessageRepository {
    fun observeTopicMessages(topicId: TopicId, currentUserId: UserId): Flow<List<Message>>
    fun observeStatistics(senderId: UserId): Flow<Statistics>
    suspend fun saveTextMessage(message: Message): Unit
    suspend fun delete(messageId: MessageId): Boolean
    suspend fun sendMediaMessage(
        topicId: TopicId,
        senderId: UserId,
        messageType: MessageType,
        srcMediaCopySource: MessageMediaCopySource,
    ): Unit

    suspend fun finishMediaProcessing(messageId: MessageId, savedMedia: SavedMedia)

    /**
     * run IN IO.
     * @throws Throwable
     */
    suspend fun initializeFileMessage(
        topicId: TopicId,
        senderId: UserId,
        messageType: MessageType,
        srcUriStr: UriStr,
        srcMetadata: MediaMetadataUnion,
        messageTimestampMs: Long = System.currentTimeMillis(),
    ): MessageId

    /**
     * run IN IO.
     * @throws Throwable
     * @return if set success (based on affected rows)
     */
    suspend fun setFileProcessingInternalCacheFilename(
        messageId: MessageId,
        filename: String?,
    ): Boolean

    /**
     * run IN IO.
     * @throws Throwable
     * @return if set success (based on affected rows)
     */
    suspend fun setFileMessageRawFilename(
        messageId: MessageId,
        filename: String?,
    ): Boolean

    /**
     * run IN IO. update message_file_processing_task_states db.
     * @throws Throwable
     * @return if set success (based on affected rows)
     */
    suspend fun setFileProcessingTaskFail(
        messageId: MessageId,
        stage: MessageProcessingErrorStage,
        errorUserRetryable: Boolean,
    ): Boolean
}