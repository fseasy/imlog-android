package top.fseasy.imlog.domain.usecase

import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.RetryModel
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.DbRunner
import top.fseasy.imlog.domain.repository.MessageRepository
import top.fseasy.imlog.domain.repository.TopicRepository
import javax.inject.Inject
import kotlin.time.Instant

class SendTextMessageUseCase
@Inject
constructor(
    private val messageRepository: MessageRepository,
    private val topicRepository: TopicRepository,
    private val dbRunner: DbRunner,
) {

  /**
   * 1. insert text to messages 2. update last-read-message-id for topic-state
   *
   * @throws Throwable
   */
  suspend operator fun invoke(
      topicId: TopicId,
      senderId: UserId,
      quotedMessageId: MessageId?,
      text: String,
      createdAt: Instant,
  ): MessageId =
      dbRunner.runTransactionInIOThread(retry = RetryModel.OnAnyException) {
        val messageId =
            messageRepository.syncInsertTextMessage(
                topicId = topicId,
                senderId = senderId,
                quotedMessageId = quotedMessageId,
                text = text,
                createdAt = createdAt,
            )
        topicRepository.syncUpdateTopicLastReadMessageId(
            userId = senderId,
            topicId = topicId,
            messageId = messageId,
        )
        messageId
      }
}
