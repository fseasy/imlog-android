package top.fseasy.imlog.features.home.topiclog.timeline

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import java.nio.file.Path
import kotlin.time.Duration
import top.fseasy.imlog.domain.model.AbsolutePathModel
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.ui.model.UserAvatarUiModel

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
  sealed interface Attachment : MessageContentUiModel {
    // We parse the stored file Uri lazily, so here just store the file name again.
    // NOTE: When Rendering message bubble, we don't need the storage Uri at all.
    //       What's more, get the storage uri will go to SAF system, which is time-consuming.
    //       so it's a SUSPEND function! We have to change the signature to suspend fun.
    //       we can do it as .map support the suspend fun. BUT certainly, we don't need it!
    val storedFilename: String?
  }

  @Immutable
  sealed interface AudioPlaySupported : Attachment {
    val duration: kotlin.time.Duration
  }

  @Immutable
  sealed interface ImageLike : Attachment {
    // 1. from src uri 2. from real thumbnail path 3. illegal state (null)
    val thumbnailPath: AbsolutePathModel?
    val width: Int
    val height: Int
  }

  val ImageLike.ratio: Double
    get() = if (width == 0) 0.0 else height.toDouble() / width

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
      override val duration: Duration,
      val amplitudes: List<Float>,
  ) : AudioPlaySupported

  @Immutable
  data class Audio(
      override val storedFilename: String?,
      val sourceTemporaryUri: Uri?,
      val displayFilename: String,
      override val duration: Duration,
      val amplitudes: List<Float>,
      val mimeType: String,
  ) : AudioPlaySupported

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
    val content: MessageContentUiModel,
    val createdAt: kotlin.time.Instant,
    val formatedCreatedAt: String,
) {
  fun supportAudioPlay() = content is MessageContentUiModel.AudioPlaySupported
}
