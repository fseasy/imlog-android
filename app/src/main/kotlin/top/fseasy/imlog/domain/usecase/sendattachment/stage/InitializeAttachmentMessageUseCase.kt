package top.fseasy.imlog.domain.usecase.sendattachment.stage

import javax.inject.Inject
import kotlin.time.Instant
import top.fseasy.imlog.domain.model.FileMetadataUnion
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.RetryModel
import top.fseasy.imlog.domain.model.StoragePathModel
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.DbRunner
import top.fseasy.imlog.domain.repository.MessageRepository
import top.fseasy.imlog.domain.repository.TopicRepository

class InitializeAttachmentMessageUseCase
@Inject
constructor(
    private val messageRepository: MessageRepository,
    private val topicRepository: TopicRepository,
    private val dbRunner: DbRunner,
) {

  /**
   * insert/update records for following tables
   * 1. message
   * 2. message_attachment_processing_task_states
   * 3. topic (last-read-message-id)
   *
   * @throws Throwable
   */
  suspend operator fun invoke(
      srcPath: StoragePathModel,
      senderId: UserId,
      topicId: TopicId,
      messageTimestamp: Instant,
      messageType: MessageType,
      fileMetadata: FileMetadataUnion,
  ): MessageId =
      dbRunner.runTransactionInIOThread(retry = RetryModel.OnAnyException) {
        val messageId =
            messageRepository.syncInsertInitialAttachmentMessage(
                topicId = topicId,
                senderId = senderId,
                type = messageType,
                createdAt = messageTimestamp,
                fileMetadata = fileMetadata,
                quotedMessageId = null,
            )
        messageRepository.syncInsertInitialAttachmentProcessingTaskState(
            messageId = messageId,
            srcPath = srcPath,
            taskStartTime = messageTimestamp,
        )
        topicRepository.syncUpdateTopicLastReadMessageId(
            userId = senderId,
            topicId = topicId,
            messageId = messageId,
        )
        messageId
      }
}
