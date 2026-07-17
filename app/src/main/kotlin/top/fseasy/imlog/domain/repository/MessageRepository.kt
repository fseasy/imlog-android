package top.fseasy.imlog.domain.repository

import kotlinx.coroutines.flow.Flow
import top.fseasy.imlog.domain.model.FileMetadataUnion
import top.fseasy.imlog.domain.model.Message
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.MessageProcessingErrorStage
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.Statistics
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UriStr
import top.fseasy.imlog.domain.model.UserId


sealed interface MessageFileSource {
    data class FromUriStr(val uriStr: UriStr) : MessageFileSource

    /***
     * The cache file follows the Storage Path rule, so just pass the filename.
     * Mainly used for recording.
     * Use this to restrict the api.
     */
    data class FromMessageCacheFile(val filename: String) : MessageFileSource
}

interface MessageRepository {
    fun observeTopicMessages(topicId: TopicId, currentUserId: UserId): Flow<List<Message>>
    fun observeStatistics(senderId: UserId): Flow<Statistics>


    // ==============
    // SYNC api for upper compose. Don't use directly!
    // ==============
    /***
     * SYNC create an initial file message (without raw-file and thumbnail), insert to db and
     * return message id
     *
     * WRAP it in IO threads!
     */
    fun syncInsertInitialFileMessage(
        topicId: TopicId,
        senderId: UserId,
        type: MessageType,
        timestampMs: Long,
        fileMetadata: FileMetadataUnion,
    ): MessageId

    fun syncInsertInitialFileProcessingTaskState(
        messageId: MessageId,
        fileSource: MessageFileSource,
        taskStartTime: Long,
    )

    // ================
    // Run in IO threads Apis
    // ================

    suspend fun saveTextMessage(message: Message): Unit
    suspend fun delete(messageId: MessageId): Boolean

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

    suspend fun deleteFileProcessingTaskState(
        messageId: MessageId,
    ): Boolean
}