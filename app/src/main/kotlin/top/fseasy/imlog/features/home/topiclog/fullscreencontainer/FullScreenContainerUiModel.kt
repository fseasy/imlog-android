package top.fseasy.imlog.features.home.topiclog.fullscreencontainer

import androidx.compose.runtime.Immutable
import top.fseasy.imlog.domain.model.AbsolutePathModel
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.features.home.topiclog.timeline.MessageContentUiModel
import top.fseasy.imlog.features.home.topiclog.timeline.MessageUiModel

/** For Full Screen show. */
@Immutable
sealed interface FullScreenContainerUiModel {
  val id: MessageId

  @Immutable
  data class ImageShow(
      val message: MessageUiModel<MessageContentUiModel.Image>,
      val path: AbsolutePathModel,
  ) : FullScreenContainerUiModel {
    override val id: MessageId
      get() = message.id
  }

  @Immutable
  data class VideoShow(
      val message: MessageUiModel<MessageContentUiModel.Video>,
      val path: AbsolutePathModel,
  ) : FullScreenContainerUiModel {
    override val id: MessageId
      get() = message.id
  }

  @Immutable
  data class TextSelection(
      val message: MessageUiModel<MessageContentUiModel.Text>,
  ) : FullScreenContainerUiModel {
    override val id: MessageId
      get() = message.id
  }
}
