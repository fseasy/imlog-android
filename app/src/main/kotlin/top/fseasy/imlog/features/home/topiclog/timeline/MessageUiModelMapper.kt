package top.fseasy.imlog.features.home.topiclog.timeline

import android.content.Context
import android.net.Uri
import top.fseasy.imlog.data.mapper.toAbsolutePathWithoutCreating
import top.fseasy.imlog.data.mapper.toNioPath
import top.fseasy.imlog.data.mapper.toUriOrNull
import top.fseasy.imlog.data.mapper.toUriOrThrow
import top.fseasy.imlog.domain.model.AbsolutePathModel
import top.fseasy.imlog.domain.model.CacheAttachmentSource
import top.fseasy.imlog.domain.model.MessageContent
import top.fseasy.imlog.domain.model.MessageSender
import top.fseasy.imlog.domain.model.QuotedMessage
import top.fseasy.imlog.domain.model.QuotedMessageContent
import top.fseasy.imlog.domain.model.QuotedMessageSender
import top.fseasy.imlog.domain.model.TimelineMessage
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UriAttachmentSource
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.StorageRepository
import top.fseasy.imlog.domain.usecase.StoragePathUseCase
import top.fseasy.imlog.ui.model.buildUserAvatarNioPath
import top.fseasy.imlog.ui.model.toUiModel
import top.fseasy.imlog.ui.util.ImTimeUtils
import top.fseasy.imlog.ui.util.byteSizeToHumanReadable
import top.fseasy.imlog.ui.util.mimeTypeToIconResId
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.sin
import kotlin.time.Instant
import kotlin.time.toJavaInstant

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
    createdAt: Instant,
    context: Context,
): MessageContentUiModel {

  fun getStorageFilename(
      uriAttachmentSource: UriAttachmentSource,
  ): String? = (uriAttachmentSource as? UriAttachmentSource.Storage)?.filename

  fun getSourceTemporaryUri(uriAttachmentSource: UriAttachmentSource): Uri? =
      (uriAttachmentSource as? UriAttachmentSource.SourceTemporary)?.uriStr?.toUriOrNull()

  fun buildThumbnailPath(
      uriAttachmentSource: UriAttachmentSource,
      thumbnailFilename: String?,
      createdAt: Instant,
  ): AbsolutePathModel? {
    return if (thumbnailFilename != null) {
      storagePathUseCase
          .buildMessageThumbnailStoragePath(
              userId = signInUserId,
              topicId = topicId,
              timestampMs = createdAt.toEpochMilliseconds(),
              filename = thumbnailFilename,
          )
          .toAbsolutePathWithoutCreating(context)
    } else {
      // try to get from source temporary uri
      when (uriAttachmentSource) {
        is UriAttachmentSource.SourceTemporary ->
            AbsolutePathModel.UriStrModel(uriAttachmentSource.uriStr)
        is UriAttachmentSource.Storage ->
            null // currently we don't render thumbnail from storage part
        is UriAttachmentSource.IllegalState -> null
      }
    }
  }

  fun buildCacheFilePath(file: CacheAttachmentSource): Path? {
    val cacheFilename = (file as? CacheAttachmentSource.Cache)?.filename ?: return null
    return storagePathUseCase
        .buildMessageCacheFileStoragePath(
            userId = signInUserId,
            filename = cacheFilename,
        )
        .toNioPath(context)
  }

  return when (this) {
    is MessageContent.Text -> MessageContentUiModel.Text(text = text)
    is MessageContent.GenericFile ->
        MessageContentUiModel.GenericFile(
            storedFilename = getStorageFilename(fileUri),
            sourceTemporaryUri = getSourceTemporaryUri(fileUri),
            displayFilename = displayFilename,
            formatedFileSize = fileByteSize.byteSizeToHumanReadable(),
            mimeType = mimeType,
            iconRes = mimeTypeToIconResId(mimeType),
        )
    is MessageContent.Image ->
        MessageContentUiModel.Image(
            storedFilename = getStorageFilename(fileUri),
            thumbnailPath =
                buildThumbnailPath(
                    uriAttachmentSource = fileUri,
                    thumbnailFilename = thumbnailFilename,
                    createdAt = createdAt,
                ),
            width = width,
            height = height,
        )
    is MessageContent.Video ->
        MessageContentUiModel.Video(
            storedFilename = getStorageFilename(fileUri),
            thumbnailPath =
                buildThumbnailPath(
                    uriAttachmentSource = fileUri,
                    thumbnailFilename = thumbnailFilename,
                    createdAt = createdAt,
                ),
            width = width,
            height = height,
            duration = duration,
        )
    is MessageContent.Voice ->
        MessageContentUiModel.Voice(
            storedFilename = (file as? CacheAttachmentSource.StorageUri)?.filename,
            cachePath = buildCacheFilePath(file),
            duration = duration,
            amplitudes = generateDummyAmplitudes(duration),
        )
    is MessageContent.Audio ->
        MessageContentUiModel.Audio(
            storedFilename = getStorageFilename(fileUri),
            sourceTemporaryUri = getSourceTemporaryUri(fileUri),
            displayFilename = displayFilename,
            duration = duration,
            amplitudes = generateDummyAmplitudes(duration),
            mimeType = mimeType,
        )
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
      content =
          content.toUiModel(
              signInUserId = signInUserId,
              topicId = topicId,
              storagePathUseCase = storagePathUseCase,
              createdAt = createdAt,
              context = context,
          ),
      createdAt = createdAt,
      formatedCreatedAt = ImTimeUtils.formatImTime(createdAt.toJavaInstant()),
  )
}

/** @throws Exception when resolve Uri */
internal suspend fun MessageContentUiModel.Attachment.buildStorageUri(
    signInUserId: UserId,
    topicId: TopicId,
    messageCreatedAt: kotlin.time.Instant,
    storagePathUseCase: StoragePathUseCase,
    storageRepository: StorageRepository,
): Uri? {
  val filename = storedFilename ?: return null
  val storagePath =
      storagePathUseCase.buildMessageRawFileStoragePath(
          userId = signInUserId,
          topicId = topicId,
          timestampMs = messageCreatedAt.toEpochMilliseconds(),
          filename = filename,
      )
  val uriModel =
      storageRepository.resolveSharedStoragePathToAbsolutePathWithoutCreating(storagePath)
  return uriModel.value.toUriOrThrow()
}

/** @throws Exception when resolve Uri */
internal suspend fun MessageContentUiModel.AudioPlaySupported.buildUri(
    signInUserId: UserId,
    topicId: TopicId,
    messageCreatedAt: kotlin.time.Instant,
    audioSupportedContent: MessageContentUiModel.AudioPlaySupported,
    storagePathUseCase: StoragePathUseCase,
    storageRepository: StorageRepository,
): Uri? {
  suspend fun buildStorageUri() =
      audioSupportedContent.buildStorageUri(
          signInUserId = signInUserId,
          topicId = topicId,
          messageCreatedAt = messageCreatedAt,
          storagePathUseCase = storagePathUseCase,
          storageRepository = storageRepository,
      )
  return when (audioSupportedContent) {
    is MessageContentUiModel.Voice -> {
      // Prefer to cache path if it exists.
      val cachePath = audioSupportedContent.cachePath
      if (cachePath != null && cachePath.exists()) {
        Uri.fromFile(cachePath.toFile())
      } else buildStorageUri()
    }
    // Prefer source temporary uri
    is MessageContentUiModel.Audio -> audioSupportedContent.sourceTemporaryUri ?: buildStorageUri()
  }
}

private fun generateDummyAmplitudes(duration: kotlin.time.Duration): List<Float> {
  val size = (duration.inWholeSeconds * 4).toInt()
  val salt = duration.inWholeMilliseconds % PI
  return (0 until size).map { i ->
    val position = i.toFloat() / size + salt
    val envelope = sin(position * PI).toFloat().absoluteValue * 0.8f + 0.2f
    val detail = sin(position * 15 * PI).toFloat().absoluteValue * 0.5f + 0.5f
    (envelope * detail * 0.8f + 0.2f).coerceIn(0.1f, 1f)
  }
}
