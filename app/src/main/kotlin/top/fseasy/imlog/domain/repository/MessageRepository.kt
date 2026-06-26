package top.fseasy.imlog.domain.repository

import kotlinx.coroutines.flow.Flow
import top.fseasy.imlog.domain.model.MediaMetadataUnion
import top.fseasy.imlog.domain.model.Message
import top.fseasy.imlog.domain.model.MessageFileProcessingErrorType
import top.fseasy.imlog.domain.model.MessageFileProcessingStage
import top.fseasy.imlog.domain.model.MessageFileProcessingStatus
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.MessageMediaCopySource
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
    suspend fun fileSendingOnInsertingPendingMessage(
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
    suspend fun fileSendingOnSettingInternalCacheFilename(
        messageId: MessageId,
        filename: String?,
    ): Boolean

    /**
     * run IN IO.
     * @throws Throwable
     * @return if set success (based on affected rows)
     */
    suspend fun fileSendingOnSettingRawFilename(
        messageId: MessageId,
        filename: String?,
    ): Boolean

    /**
     * run IN IO.
     * @throws Throwable
     * @return if set success (based on affected rows)
     */
    suspend fun fileSendingOnSettingProcessingStatus(
        messageId: MessageId,
        status: MessageFileProcessingStatus,
        stage: MessageFileProcessingStage,
        errorType: MessageFileProcessingErrorType,
        errorUserRetriable: Boolean,
    ): Boolean
}