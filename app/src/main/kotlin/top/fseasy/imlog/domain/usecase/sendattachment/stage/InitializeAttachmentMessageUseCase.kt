package top.fseasy.imlog.domain.usecase.sendattachment.stage

import top.fseasy.imlog.domain.model.FileMetadataUnion
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.RetryModel
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UriStr
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.DbRunner
import top.fseasy.imlog.domain.repository.MessageAttachmentSource
import top.fseasy.imlog.domain.repository.MessageRepository
import javax.inject.Inject

class InitializeAttachmentMessageUseCase
@Inject
constructor(
    private val messageRepository: MessageRepository,
    private val dbRunner: DbRunner,
) {

  /** @throws Throwable */
  suspend fun forUriSource(
      srcUriStr: UriStr,
      senderId: UserId,
      topicId: TopicId,
      messageTimestampMs: Long,
      messageType: MessageType,
      fileMetadata: FileMetadataUnion,
  ): MessageId =
      dbRunner.runTransactionInIOThread(retry = RetryModel.OnAnyException) {
        val messageId =
            messageRepository.syncInsertInitialAttachmentMessage(
                topicId = topicId,
                senderId = senderId,
                type = messageType,
                timestampMs = messageTimestampMs,
                fileMetadata = fileMetadata,
            )
        messageRepository.syncInsertInitialAttachmentProcessingTaskState(
            messageId = messageId,
            fileSource = MessageAttachmentSource.FromUriStr(srcUriStr),
            taskStartTime = messageTimestampMs,
        )
        messageId
      }

  /**
   * @param cacheFilename: obey the @StoragePathUseCase.buildMessageCacheFileStoragePath
   * @throws Throwable
   */
  suspend fun forCacheFileSource(
      cacheFilename: String,
      senderId: UserId,
      topicId: TopicId,
      messageTimestampMs: Long,
      messageType: MessageType,
      fileMetadata: FileMetadataUnion,
  ): MessageId =
      dbRunner.runTransactionInIOThread(retry = RetryModel.OnAnyException) {
        val messageId =
            messageRepository.syncInsertInitialAttachmentMessage(
                topicId = topicId,
                senderId = senderId,
                type = messageType,
                timestampMs = messageTimestampMs,
                fileMetadata = fileMetadata,
            )
        messageRepository.syncInsertInitialAttachmentProcessingTaskState(
            messageId = messageId,
            fileSource = MessageAttachmentSource.FromMessageCache(cacheFilename),
            taskStartTime = messageTimestampMs,
        )
        messageId
      }
}
