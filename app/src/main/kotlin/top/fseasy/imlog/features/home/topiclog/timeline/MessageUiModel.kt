package top.fseasy.imlog.features.home.topiclog.timeline

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import top.fseasy.imlog.domain.model.AbsolutePathModel
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.ui.model.UserAvatarUiModel
import java.nio.file.Path
import kotlin.time.Duration

@Serializable
sealed interface SenderUiModel {
  val avatar: UserAvatarUiModel

  data class Own(override val avatar: UserAvatarUiModel) : SenderUiModel

  data class Other(val name: String, override val avatar: UserAvatarUiModel) : SenderUiModel
}

sealed interface QuotedMessageContentUiModel {
  data class Text(val text: String) : QuotedMessageContentUiModel

  // audio, generic file
  data class File(val filename: String, val mimeType: String) : QuotedMessageContentUiModel

  data class Voice(val duration: Duration) : QuotedMessageContentUiModel

  data class Image(val path: Path) : QuotedMessageContentUiModel

  data class Video(val path: Path) : QuotedMessageContentUiModel
}

data class QuotedMessageUiModel(
    val senderName: String,
    val content: QuotedMessageContentUiModel,
)

@Immutable
sealed interface MessageContentUiModel {

  @Immutable data class Text(val text: String) : MessageContentUiModel

  @Immutable
  sealed interface Attachment {
    val fileAbsolutePath: AbsolutePathModel
  }

  @Immutable
  sealed interface ImageLike : Attachment {
    val thumbnailPath: Path?
    val width: Int
    val height: Int
  }

  @Immutable
  data class Image(
      override val fileAbsolutePath: AbsolutePathModel,
      override val thumbnailPath: Path?,
      override val width: Int,
      override val height: Int,
  ) : ImageLike

  @Immutable
  data class Video(
      override val fileAbsolutePath: AbsolutePathModel,
      override val thumbnailPath: Path?,
      override val width: Int,
      override val height: Int,
      val duration: Duration,
  ) : ImageLike

  @Immutable
  data class Voice(
      override val fileAbsolutePath: AbsolutePathModel,
      val duration: Duration,
  ) : Attachment

  @Immutable
  data class Audio(
      override val fileAbsolutePath: AbsolutePathModel,
      val displayFilename: String,
      val duration: Duration,
  ) : Attachment

  @Immutable
  data class GenericFile(
      override val fileAbsolutePath: AbsolutePathModel,
      val displayName: String,
      val fileSize: String,
      val mimeType: String,
  ) : Attachment
}

@Immutable
data class MessageUiModel(
    val id: MessageId,
    val sender: SenderUiModel,
    val quotedMessage: QuotedMessageUiModel? = null,
    val createdAt: String,
    val content: MessageContentUiModel,
)
