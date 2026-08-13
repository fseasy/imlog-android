package top.fseasy.imlog.features.home.topiclog.timeline

import android.net.Uri
import android.os.Parcelable
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import java.nio.file.Path
import kotlin.time.Duration
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import top.fseasy.imlog.data.mapper.AbsolutePathModelParceler
import top.fseasy.imlog.data.mapper.MessageIdParceler
import top.fseasy.imlog.data.mapper.UserIdParceler
import top.fseasy.imlog.data.util.NioPathParceler
import top.fseasy.imlog.domain.model.AbsolutePathModel
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.features.home.topiclog.timeline.MessageContentUiModel.ImageLike
import top.fseasy.imlog.ui.model.UserAvatarUiModel

@Parcelize
sealed interface QuotedMessageSenderUiModel : Parcelable {
  @Parcelize data object Own : QuotedMessageSenderUiModel

  @Parcelize data class Other(val name: String) : QuotedMessageSenderUiModel
}

@Parcelize
sealed interface MessageSenderUiModel : Parcelable {
  val avatar: UserAvatarUiModel

  @Parcelize data class Own(override val avatar: UserAvatarUiModel) : MessageSenderUiModel

  @Parcelize
  @TypeParceler<UserId, UserIdParceler>
  data class Other(val id: UserId, val name: String, override val avatar: UserAvatarUiModel) :
      MessageSenderUiModel
}

@Parcelize
sealed interface QuotedMessageContentUiModel : Parcelable {
  @Parcelize data class Text(val text: String) : QuotedMessageContentUiModel

  // audio, generic file
  @Parcelize
  data class File(val displayFilename: String, @DrawableRes val iconRes: Int) :
      QuotedMessageContentUiModel

  @Parcelize data class Voice(val duration: Duration) : QuotedMessageContentUiModel

  @Parcelize
  @TypeParceler<Path?, NioPathParceler>()
  data class Image(val path: Path?) : QuotedMessageContentUiModel

  @Parcelize
  @TypeParceler<Path?, NioPathParceler>()
  data class Video(val path: Path?) : QuotedMessageContentUiModel
}

@Parcelize
sealed interface QuotedMessageUiModel : Parcelable {
  @Parcelize data object Deleted : QuotedMessageUiModel

  @Parcelize
  data class Matched(
      val sender: QuotedMessageSenderUiModel,
      val content: QuotedMessageContentUiModel,
  ) : QuotedMessageUiModel
}

@Immutable
@Parcelize
sealed interface MessageContentUiModel : Parcelable {

  @Immutable @Parcelize data class Text(val text: String) : MessageContentUiModel

  @Immutable
  @Parcelize
  sealed interface Attachment : MessageContentUiModel {
    // We parse the stored file Uri lazily, so here just store the file name again.
    // NOTE: When Rendering message bubble, we don't need the storage Uri at all.
    //       What's more, get the storage uri will go to SAF system, which is time-consuming.
    //       so it's a SUSPEND function! We have to change the signature to suspend fun.
    //       we can do it as .map support the suspend fun. BUT certainly, we don't need it!
    val storedFilename: String?
  }

  @Immutable
  @Parcelize
  sealed interface AudioPlaySupported : Attachment {
    val duration: kotlin.time.Duration
  }

  @Immutable
  @Parcelize
  sealed interface ImageLike : Attachment {
    // 1. from src uri 2. from real thumbnail path 3. illegal state (null)
    val thumbnailPath: AbsolutePathModel?
    val width: Int
    val height: Int
  }

  @Immutable
  @Parcelize
  @TypeParceler<AbsolutePathModel?, AbsolutePathModelParceler>()
  data class Image(
      override val storedFilename: String?,
      override val thumbnailPath: AbsolutePathModel?,
      override val width: Int,
      override val height: Int,
  ) : ImageLike

  @Immutable
  @Parcelize
  @TypeParceler<AbsolutePathModel?, AbsolutePathModelParceler>()
  data class Video(
      override val storedFilename: String?,
      override val thumbnailPath: AbsolutePathModel?,
      override val width: Int,
      override val height: Int,
      val duration: Duration,
  ) : ImageLike

  @Immutable
  @Parcelize
  @TypeParceler<Path?, NioPathParceler>
  data class Voice(
      override val storedFilename: String?,
      val cachePath: Path?,
      override val duration: Duration,
      val amplitudes: List<Float>,
  ) : AudioPlaySupported

  @Immutable
  @Parcelize
  data class Audio(
      override val storedFilename: String?,
      val sourceTemporaryUri: Uri?,
      val displayFilename: String,
      override val duration: Duration,
      val amplitudes: List<Float>,
      val mimeType: String,
  ) : AudioPlaySupported

  @Immutable
  @Parcelize
  data class GenericFile(
      override val storedFilename: String?,
      val sourceTemporaryUri: Uri?,
      val displayFilename: String,
      val formatedFileSize: String,
      val mimeType: String,
      @DrawableRes val iconRes: Int,
  ) : Attachment
}

@Immutable
@Parcelize
@TypeParceler<MessageId, MessageIdParceler>
data class MessageUiModel(
    val id: MessageId,
    val sender: MessageSenderUiModel,
    val quotedMessage: QuotedMessageUiModel? = null,
    val content: MessageContentUiModel,
    val createdAt: kotlin.time.Instant,
    val formatedCreatedAt: String,
) : Parcelable {
  fun supportAudioPlay() = content is MessageContentUiModel.AudioPlaySupported
}

val ImageLike.aspectRatio: Float
  get() = if (height == 0) 1.0f else width.toFloat() / height
