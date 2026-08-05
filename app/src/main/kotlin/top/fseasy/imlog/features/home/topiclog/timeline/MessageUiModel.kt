package top.fseasy.imlog.features.home.topiclog.timeline

import android.content.Context
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import timber.log.Timber
import top.fseasy.imlog.data.mapper.toNioPath
import top.fseasy.imlog.domain.model.AbsolutePathModel
import top.fseasy.imlog.domain.model.MessageContent
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.MessageSender
import top.fseasy.imlog.domain.model.QuotedMessage
import top.fseasy.imlog.domain.model.QuotedMessageContent
import top.fseasy.imlog.domain.model.QuotedMessageSender
import top.fseasy.imlog.domain.model.TimelineMessage
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UriAttachmentSource
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.usecase.StoragePathUseCase
import top.fseasy.imlog.ui.model.UserAvatarUiModel
import top.fseasy.imlog.ui.model.buildUserAvatarNioPath
import top.fseasy.imlog.ui.model.toUiModel
import top.fseasy.imlog.ui.util.ImTimeUtils
import top.fseasy.imlog.ui.util.mimeTypeToIconResId
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.time.toJavaInstant

sealed interface QuotedMessageSenderUiModel {
  data object Own : QuotedMessageSenderUiModel

  data class Other(val name: String) : QuotedMessageSenderUiModel
}

sealed interface MessageSenderUiModel {
  val avatar: UserAvatarUiModel

  data class Own(override val avatar: UserAvatarUiModel) : MessageSenderUiModel

  data class Other(val id: UserId, val name: String, override val avatar: UserAvatarUiModel) :
      MessageSenderUiModel
}

sealed interface QuotedMessageContentUiModel {
  data class Text(val text: String) : QuotedMessageContentUiModel

  // audio, generic file
  data class File(val displayFilename: String, @DrawableRes val iconRes: Int) :
      QuotedMessageContentUiModel

  data class Voice(val duration: Duration) : QuotedMessageContentUiModel

  data class Image(val path: Path?) : QuotedMessageContentUiModel

  data class Video(val path: Path?) : QuotedMessageContentUiModel
}

sealed interface QuotedMessageUiModel {
  data object Deleted : QuotedMessageUiModel

  data class Matched(
      val sender: QuotedMessageSenderUiModel,
      val content: QuotedMessageContentUiModel,
  ) : QuotedMessageUiModel
}

@Immutable
sealed interface MessageContentUiModel {

  @Immutable data class Text(val text: String) : MessageContentUiModel

  @Immutable
  sealed interface Attachment {
    // We don't parse the Uri here, leaving it when user click the resource
    val storedFilename: String?
  }

  @Immutable
  sealed interface ImageLike : Attachment {
    // 1. from src uri 2. from real thumbnail path 3. illegal state (null)
    val thumbnailPath: AbsolutePathModel?
    val width: Int
    val height: Int
  }

  @Immutable
  data class Image(
      override val storedFilename: String?,
      override val thumbnailPath: AbsolutePathModel?,
      override val width: Int,
      override val height: Int,
  ) : ImageLike

  @Immutable
  data class Video(
      override val storedFilename: String?,
      override val thumbnailPath: AbsolutePathModel?,
      override val width: Int,
      override val height: Int,
      val duration: Duration,
  ) : ImageLike

  @Immutable
  data class Voice(
      override val storedFilename: String?,
      val cachePath: Path?,
      val duration: Duration,
  ) : Attachment

  @Immutable
  data class Audio(
      override val storedFilename: String?,
      val sourceTemporaryUri: Uri?,
      val displayFilename: String,
      val duration: Duration,
  ) : Attachment

  @Immutable
  data class GenericFile(
      override val storedFilename: String?,
      val sourceTemporaryUri: Uri?,
      val displayFilename: String,
      val formatedFileSize: String,
      @DrawableRes val iconRes: Int,
  ) : Attachment
}

@Immutable
data class MessageUiModel(
    val id: MessageId,
    val sender: MessageSenderUiModel,
    val quotedMessage: QuotedMessageUiModel? = null,
    val createdAt: String,
    val content: MessageContentUiModel,
)

fun QuotedMessageSender.toUiModel(
    signInUserId: UserId,
): QuotedMessageSenderUiModel =
    when {
      signInUserId == id -> QuotedMessageSenderUiModel.Own
      else -> QuotedMessageSenderUiModel.Other(name = name)
    }

fun QuotedMessageContent.toUiModel(
    signInUserId: UserId,
    topicId: TopicId,
    storagePathUseCase: StoragePathUseCase,
    context: Context,
): QuotedMessageContentUiModel {

  fun buildThumbnailPath(thumbnailFilename: String, createdAt: Instant): Path =
      storagePathUseCase
          .buildMessageThumbnailStoragePath(
              userId = signInUserId,
              topicId = topicId,
              timestampMs = createdAt.toEpochMilliseconds(),
              filename = thumbnailFilename,
          )
          .toNioPath(context)

  return when (this) {
    is QuotedMessageContent.Text -> QuotedMessageContentUiModel.Text(text = text)
    is QuotedMessageContent.File ->
        QuotedMessageContentUiModel.File(
            displayFilename = displayFilename,
            iconRes = mimeTypeToIconResId(mimeType),
        )
    is QuotedMessageContent.Image ->
        QuotedMessageContentUiModel.Image(
            path =
                thumbnailFilename?.let {
                  buildThumbnailPath(
                      thumbnailFilename = thumbnailFilename,
                      createdAt = createdAt,
                  )
                }
        )
    is QuotedMessageContent.Video ->
        QuotedMessageContentUiModel.Video(
            path =
                thumbnailFilename?.let {
                  buildThumbnailPath(
                      thumbnailFilename = thumbnailFilename,
                      createdAt = createdAt,
                  )
                }
        )
    is QuotedMessageContent.Voice -> QuotedMessageContentUiModel.Voice(duration = duration)
  }
}

fun QuotedMessage.toUiModel(
    signInUserId: UserId,
    topicId: TopicId,
    storagePathUseCase: StoragePathUseCase,
    context: Context,
): QuotedMessageUiModel =
    when (this) {
      QuotedMessage.Deleted -> QuotedMessageUiModel.Deleted
      is QuotedMessage.Matched ->
          QuotedMessageUiModel.Matched(
              sender = sender.toUiModel(signInUserId),
              content =
                  content.toUiModel(
                      signInUserId = signInUserId,
                      topicId = topicId,
                      storagePathUseCase = storagePathUseCase,
                      context = context,
                  ),
          )
    }

fun MessageSender.toUiModel(
    signInUserId: UserId,
    storagePathUseCase: StoragePathUseCase,
    context: Context,
): MessageSenderUiModel {
  val avatarUiModel = avatarModel.toUiModel { filename ->
    buildUserAvatarNioPath(
        signInUserId = signInUserId,
        storagePathUseCase = storagePathUseCase,
        context = context,
        filename = filename,
    )
  }
  return if (signInUserId == id) {
    MessageSenderUiModel.Own(avatarUiModel)
  } else {
    MessageSenderUiModel.Other(
        id = id,
        name = name,
        avatar = avatarUiModel,
    )
  }
}

fun MessageContent.toUiModel(
    signInUserId: UserId,
    topicId: TopicId,
    storagePathUseCase: StoragePathUseCase,
    context: Context,
): MessageContentUiModel {

  fun buildThumbnailPath(thumbnailFilename: String, createdAt: Instant): Path =
      storagePathUseCase
          .buildMessageThumbnailStoragePath(
              userId = signInUserId,
              topicId = topicId,
              timestampMs = createdAt.toEpochMilliseconds(),
              filename = thumbnailFilename,
          )
          .toNioPath(context)

  fun buildAbsolutePath(
      uriAttachmentSource: UriAttachmentSource,
      messageCreatedAt: Instant,
  ): AbsolutePathModel? {
    return when (uriAttachmentSource) {
      UriAttachmentSource.IllegalState -> {
        Timber.w(
            "Attachment Message get illegal uri attachment state: uid=$signInUserId, topicId=$topicId"
        )
        null
      }
      is UriAttachmentSource.SourceTemporary ->
          AbsolutePathModel.UriStrModel(uriAttachmentSource.uriStr)
      is UriAttachmentSource.Storage -> {
        // NOTE: When Rendering message bubble, we don't need the storage Uri at all.
        //       What's more, get the storage uri will go to SAF system, which is time-consuming.
        //       so it's a SUSPEND function! We have to change the signature to suspend fun.
        //       we can do it as .map support the suspend fun. BUT certainly, we don't need it!
        null
      }
    }
  }

  return when (this) {
    is MessageContent.Text -> MessageContentUiModel.Text(text = text)
    is MessageContent.GenericFile ->
        MessageContentUiModel.GenericFile(
            displayFilename = displayFilename,
            fileAbsolutePath = TODO(),
            formatedFileSize = TODO(),
            iconRes = mimeTypeToIconResId(mimeType),
        )
    is MessageContent.Image ->
        MessageContentUiModel.Image(
            path =
                thumbnailFilename?.let {
                  buildThumbnailPath(
                      thumbnailFilename = thumbnailFilename,
                      createdAt = createdAt,
                  )
                }
        )
    is MessageContent.Video ->
        MessageContentUiModel.Video(
            path =
                thumbnailFilename?.let {
                  buildThumbnailPath(
                      thumbnailFilename = thumbnailFilename,
                      createdAt = createdAt,
                  )
                }
        )
    is MessageContent.Voice -> MessageContentUiModel.Voice(duration = duration)
  }
}

fun TimelineMessage.toUiModel(
    signInUserId: UserId,
    topicId: TopicId,
    storagePathUseCase: StoragePathUseCase,
    context: Context,
): MessageUiModel {
  return MessageUiModel(
      id = id,
      sender =
          sender.toUiModel(
              signInUserId = signInUserId,
              storagePathUseCase = storagePathUseCase,
              context = context,
          ),
      quotedMessage =
          quotedMessage?.toUiModel(
              signInUserId = signInUserId,
              topicId = topicId,
              storagePathUseCase = storagePathUseCase,
              context = context,
          ),
      createdAt = ImTimeUtils.formatImTime(createdAt.toJavaInstant()),
      content = TODO(),
  )
}
