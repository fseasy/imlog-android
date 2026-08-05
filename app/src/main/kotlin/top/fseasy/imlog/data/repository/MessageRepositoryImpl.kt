package top.fseasy.imlog.data.repository

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.paging3.QueryPagingSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import top.fseasy.imlog.R
import top.fseasy.imlog.data.util.MimeTypeUtils
import top.fseasy.imlog.data.util.retryOnAnyException
import top.fseasy.imlog.data.util.retrySQLiteOnKeyConflict
import top.fseasy.imlog.domain.model.AvatarModel
import top.fseasy.imlog.domain.model.CacheAttachmentSource
import top.fseasy.imlog.domain.model.FileMetadataUnion
import top.fseasy.imlog.domain.model.MessageContent
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.MessageProcessingErrorStage
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.QuotedMessage
import top.fseasy.imlog.domain.model.QuotedMessageContent
import top.fseasy.imlog.domain.model.Sender
import top.fseasy.imlog.domain.model.Statistics
import top.fseasy.imlog.domain.model.TimelineMessage
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UriAttachmentSource
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.MessageAttachmentSource
import top.fseasy.imlog.domain.repository.MessageRepository
import top.fseasy.imlog.sqldelight.SqlDelightDb
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import top.fseasy.imlog.sqldelight.GetPagedMessages as PagedMessagesEntity
import top.fseasy.imlog.sqldelight.Message_attachment_processing_task_states as FileProcessingTaskStateEntity
import top.fseasy.imlog.sqldelight.Messages as MessageEntity

@Singleton
class MessageRepositoryImpl
@Inject
constructor(
    @param:ApplicationContext val context: Context,
    private val database: SqlDelightDb,
    private val dispatcher: CoroutineDispatcher,
) : MessageRepository {

  /** To render the timeline message list. */
  override fun pagedTopicMessages(topicId: TopicId): Flow<PagingData<TimelineMessage>> =
      Pager(
              config = PagingConfig(pageSize = 20, enablePlaceholders = false),
              pagingSourceFactory = {
                QueryPagingSource(
                    countQuery = database.messagePagingQueries.getTopicMessageCount(topicId),
                    transacter = database.messagePagingQueries,
                    context = dispatcher,
                    queryProvider = { limit, offset ->
                      database.messagePagingQueries.getPagedMessages(
                          topicId = topicId,
                          limit = limit,
                          offset = offset,
                      )
                    },
                )
              },
          )
          .flow
          .map { pagingData -> pagingData.map { messageEntity -> messageEntity.toDomain() } }

  override fun observeStatistics(senderId: UserId): Flow<Statistics> =
      database.messageStatQueries.statOneUserUsage(senderId).asFlow().mapToOne(dispatcher).map {
        Statistics(totalDays = it.total_days, totalMessages = it.total_messages)
      }

  override fun syncInsertInitialAttachmentMessage(
      topicId: TopicId,
      senderId: UserId,
      type: MessageType,
      quotedMessageId: MessageId?,
      createdAt: Instant,
      fileMetadata: FileMetadataUnion,
  ): MessageId {
    val initialMessage =
        createInitialAttachmentMessageEntity(
            topicId = topicId,
            senderId = senderId,
            type = type,
            quotedMessageId = quotedMessageId,
            createdAt = createdAt,
            srcMetadata = fileMetadata,
        )
    database.messageQueries.insertMessage(initialMessage)
    return initialMessage.id
  }

  override fun syncInsertInitialAttachmentProcessingTaskState(
      messageId: MessageId,
      fileSource: MessageAttachmentSource,
      taskStartTime: Long,
  ) {
    val initialStateEntity =
        createInitialAttachmentProcessingTaskStateEntity(
            messageId = messageId,
            fileSource = fileSource,
            taskStartTime = taskStartTime,
        )

    database.messageAttachmentProcessingQueries.insertAttachmentProcessingState(initialStateEntity)
  }

  /**
   * insert message to DB. only suitable for Text message because the other message need extra file
   * process.
   *
   * TODO: remove this when we also need some side effects when processing text message
   */
  override suspend fun insertTextMessage(
      topicId: TopicId,
      senderId: UserId,
      quotedMessageId: MessageId?,
      text: String,
      createdAt: Instant,
  ): MessageId =
      withContext(dispatcher) {
        retrySQLiteOnKeyConflict {
          val messageEntity =
              createTextMessageEntity(
                  topicId = topicId,
                  senderId = senderId,
                  quotedMessageId = quotedMessageId,
                  text = text,
                  createdAt = createdAt,
              )
          database.messageQueries.insertMessage(messageEntity)
          messageEntity.id
        }
      }

  override suspend fun delete(messageId: MessageId): Boolean =
      withContext(dispatcher) {
        database.messageQueries.deleteMessageLogical(id = messageId).value > 0L
      }

  override suspend fun setAttachmentProcessingInternalCacheFilename(
      messageId: MessageId,
      filename: String?,
  ): Boolean =
      withContext(dispatcher) {
        retryOnAnyException {
          database.messageAttachmentProcessingQueries
              .setInternalCacheFilename(
                  internalCachedFilename = filename,
                  messageId = messageId,
              )
              .value > 0L
        }
      }

  override suspend fun setAttachmentMessageRawFilename(
      messageId: MessageId,
      filename: String?,
  ): Boolean =
      withContext(dispatcher) {
        retryOnAnyException {
          database.messageQueries
              .updateMessageRawFilename(
                  filename = filename,
                  messageId = messageId,
              )
              .value > 0L
        }
      }

  override suspend fun setAttachmentMessageThumbnailFilename(
      messageId: MessageId,
      filename: String?,
  ): Boolean =
      withContext(dispatcher) {
        retryOnAnyException {
          database.messageQueries
              .updateMessageThumbnailFilename(
                  filename = filename,
                  messageId = messageId,
              )
              .value > 0L
        }
      }

  override suspend fun setAttachmentProcessingTaskFail(
      messageId: MessageId,
      stage: MessageProcessingErrorStage,
      errorUserRetryable: Boolean,
  ) =
      withContext(dispatcher) {
        retryOnAnyException {
          database.messageAttachmentProcessingQueries
              .updateProcessingError(
                  errorStage = stage.value,
                  errorUserRetryable = errorUserRetryable,
                  messageId = messageId,
              )
              .value > 0L
        }
      }

  override suspend fun deleteAttachmentProcessingTaskState(
      messageId: MessageId,
  ) =
      withContext(dispatcher) {
        retryOnAnyException {
          database.messageAttachmentProcessingQueries
              .deleteAttachmentProcessingState(messageId)
              .value > 0L
        }
      }

  private fun createInitialAttachmentProcessingTaskStateEntity(
      messageId: MessageId,
      fileSource: MessageAttachmentSource,
      taskStartTime: Long,
  ): FileProcessingTaskStateEntity {
    val (srcUriDbStr, internalCacheFilename) =
        when (fileSource) {
          is MessageAttachmentSource.FromUriStr -> fileSource.uriStr to null
          is MessageAttachmentSource.FromMessageCache -> null to fileSource.filename
        }
    return FileProcessingTaskStateEntity(
        message_id = messageId,
        src_uri = srcUriDbStr,
        internal_cached_filename = internalCacheFilename,
        task_tart_time = taskStartTime,
        error_stage = null,
        error_user_retryable = null,
    )
  }

  private fun createInitialAttachmentMessageEntity(
      topicId: TopicId,
      senderId: UserId,
      type: MessageType,
      quotedMessageId: MessageId?,
      createdAt: Instant,
      srcMetadata: FileMetadataUnion,
  ) =
      MessageEntity(
          id = MessageId.random(),
          topic_id = topicId,
          sender_id = senderId,
          type = type,
          quoted_message_id = quotedMessageId,
          text = null,
          created_at = createdAt.toEpochMilliseconds(),
          mime_type = srcMetadata.mimeType,
          width = srcMetadata.width,
          height = srcMetadata.height,
          duration = srcMetadata.duration?.inWholeMilliseconds,
          file_size = srcMetadata.fileSize,
          display_filename = srcMetadata.displayName,
          raw_filename = null,
          thumbnail_filename = null,
          attributes_updated_at = createdAt.toEpochMilliseconds(),
          is_deleted = false,
      )

  private fun createTextMessageEntity(
      topicId: TopicId,
      senderId: UserId,
      quotedMessageId: MessageId?,
      text: String,
      createdAt: Instant,
  ) =
      MessageEntity(
          id = MessageId.random(),
          topic_id = topicId,
          sender_id = senderId,
          type = MessageType.Text,
          quoted_message_id = quotedMessageId,
          text = text,
          created_at = createdAt.toEpochMilliseconds(),
          mime_type = null,
          width = null,
          height = null,
          duration = null,
          file_size = null,
          display_filename = null,
          raw_filename = null,
          thumbnail_filename = null,
          attributes_updated_at = createdAt.toEpochMilliseconds(),
          is_deleted = false,
      )

  private fun PagedMessagesEntity.toDomain(): TimelineMessage {
    fun getFallbackSenderName(senderId: UserId?) =
        senderId?.value ?: context.getString(R.string.term_deleted_user)
    fun getFallbackDisplayFilename() = context.getString(R.string.term_unknown_filename)
    val quotedMessage =
        if (quoted_message_id == null) {
          null
        } else {
          if (quoted_type == null || quoted_created_at == null) {
            // NOT NULL fields. Null values here imply the quoted message was deleted, or corrupted
            // data.
            QuotedMessage.Deleted
          } else {
            val senderName = quoted_sender_name ?: getFallbackSenderName(quoted_sender_id)
            val quotedContent =
                when (quoted_type) {
                  MessageType.Text -> QuotedMessageContent.Text(text = quoted_text.orEmpty())
                  MessageType.Image ->
                      QuotedMessageContent.Image(
                          thumbnailFilename = quoted_attachment_thumbnail_filename,
                          createdAt = Instant.fromEpochMilliseconds(quoted_created_at),
                      )
                  MessageType.Video ->
                      QuotedMessageContent.Video(
                          thumbnailFilename = quoted_attachment_thumbnail_filename,
                          createdAt = Instant.fromEpochMilliseconds(quoted_created_at),
                      )
                  MessageType.Audio,
                  MessageType.GenericFile ->
                      QuotedMessageContent.File(
                          displayFilename =
                              quoted_attachment_display_filename ?: getFallbackDisplayFilename(),
                          mimeType =
                              quoted_attachment_mime_type
                                  ?: MimeTypeUtils.getErrorDefaultMimeType(),
                      )
                  MessageType.Voice ->
                      QuotedMessageContent.Voice(
                          duration = quoted_attachment_duration?.milliseconds ?: 0.milliseconds
                      )
                }
            QuotedMessage.Matched(
                id = quoted_message_id,
                sender = Sender.QuotedMessageSender(quoted_sender_id, senderName),
                content = quotedContent,
            )
          }
        }
    val sender =
        Sender.MessageSender(
            id = sender_id,
            name = sender_name ?: getFallbackSenderName(sender_id),
            avatarModel = sender_avatar ?: AvatarModel.Preset.default(),
        )
    fun getAttachmentDisplayFilename() = attachment_display_filename ?: getFallbackDisplayFilename()

    fun buildUriAttachmentSource(): UriAttachmentSource =
        if (attachment_stored_filename != null) {
          UriAttachmentSource.Storage(attachment_stored_filename)
        } else if (attachment_src_temporary_uri != null) {
          UriAttachmentSource.SourceTemporary(attachment_src_temporary_uri)
        } else {
          UriAttachmentSource.IllegalState
        }

    fun buildCacheAttachmentSource(): CacheAttachmentSource =
        if (attachment_internal_cache_filename != null) {
          CacheAttachmentSource.Cache(attachment_internal_cache_filename)
        } else if (attachment_stored_filename != null) {
          CacheAttachmentSource.StorageUri(attachment_stored_filename)
        } else {
          CacheAttachmentSource.IllegalState
        }

    val content =
        when (type) {
          MessageType.Text -> MessageContent.Text(text = text.orEmpty())
          MessageType.Image ->
              MessageContent.Image(
                  displayFilename = getAttachmentDisplayFilename(),
                  fileUri = buildUriAttachmentSource(),
                  thumbnailFilename = attachment_thumbnail_filename,
                  width = attachment_width ?: 0,
                  height = attachment_height ?: 0,
              )
          MessageType.Video ->
              MessageContent.Video(
                  displayFilename = getAttachmentDisplayFilename(),
                  fileUri = buildUriAttachmentSource(),
                  thumbnailFilename = attachment_thumbnail_filename,
                  width = attachment_width ?: 0,
                  height = attachment_height ?: 0,
                  duration = attachment_duration?.milliseconds ?: 0.milliseconds,
              )
          MessageType.Audio ->
              MessageContent.Audio(
                  displayFilename = getAttachmentDisplayFilename(),
                  fileUri = buildUriAttachmentSource(),
                  duration = attachment_duration?.milliseconds ?: 0.milliseconds,
              )
          MessageType.Voice ->
              MessageContent.Voice(
                  displayFilename = getAttachmentDisplayFilename(),
                  file = buildCacheAttachmentSource(),
                  duration = attachment_duration?.milliseconds ?: 0.milliseconds,
              )
          MessageType.GenericFile ->
              MessageContent.GenericFile(
                  displayFilename = getAttachmentDisplayFilename(),
                  fileUri = buildUriAttachmentSource(),
                  mimeType = attachment_mime_type ?: MimeTypeUtils.getErrorDefaultMimeType(),
              )
        }
    return TimelineMessage(
        id = id,
        sender = sender,
        quotedMessage = quotedMessage,
        createdAt = Instant.fromEpochMilliseconds(created_at),
        content = content,
    )
  }
}
