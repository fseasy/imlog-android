package top.fseasy.imlog.domain.repository

import androidx.paging.PagingData
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

/**
 * Use this type to specify the attachment file source when insert initial file message. WHY don't
 * reuse the AbsolutePathModel?? -> To ensure the file follow the storage path rule.
 */
sealed interface MessageAttachmentSource {
  data class FromUriStr(val uriStr: UriStr) : MessageAttachmentSource

  /**
   * The cache file follows the Storage Path rule, so just pass the filename. Mainly used for
   * recording. Use this to restrict the api.
   */
  data class FromMessageCache(val filename: String) : MessageAttachmentSource
}

interface MessageRepository {
  /** NOTE: now we are dependent on paging-common, so it's ok for KMP */
  fun pagedTopicMessages(topicId: TopicId): Flow<PagingData<Message>>

  fun observeStatistics(senderId: UserId): Flow<Statistics>

  suspend fun saveTextMessage(message: Message): Unit

  suspend fun delete(messageId: MessageId): Boolean

  // ==============
  // File Message related Apis.
  // ==============
  /**
   * SYNC create an initial file message (without raw-file and thumbnail), insert to db and return
   * message id
   *
   * WRAP it in IO threads!
   */
  fun syncInsertInitialAttachmentMessage(
      topicId: TopicId,
      senderId: UserId,
      type: MessageType,
      timestampMs: Long,
      fileMetadata: FileMetadataUnion,
  ): MessageId

  fun syncInsertInitialAttachmentProcessingTaskState(
      messageId: MessageId,
      fileSource: MessageAttachmentSource,
      taskStartTime: Long,
  )

  /**
   * run IN IO.
   *
   * @param filename set to null to delete the filed in db
   * @return if set success (based on affected rows)
   * @throws Throwable
   */
  suspend fun setAttachmentProcessingInternalCacheFilename(
      messageId: MessageId,
      filename: String?,
  ): Boolean

  /**
   * run IN IO.
   *
   * @param filename set to null to delete the filed in db
   * @return if set success (based on affected rows)
   * @throws Throwable
   */
  suspend fun setAttachmentMessageRawFilename(
      messageId: MessageId,
      filename: String?,
  ): Boolean

  /**
   * run IN IO.
   *
   * @param filename set to null to delete the filed in db
   * @return if set success (based on affected rows)
   * @throws Throwable
   */
  suspend fun setAttachmentMessageThumbnailFilename(
      messageId: MessageId,
      filename: String?,
  ): Boolean

  /**
   * run IN IO. update message_file_processing_task_states db.
   *
   * @return if set success (based on affected rows)
   * @throws Throwable
   */
  suspend fun setAttachmentProcessingTaskFail(
      messageId: MessageId,
      stage: MessageProcessingErrorStage,
      errorUserRetryable: Boolean,
  ): Boolean

  suspend fun deleteAttachmentProcessingTaskState(
      messageId: MessageId,
  ): Boolean
}
