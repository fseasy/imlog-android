package top.fseasy.imlog.features.home.topiclog.fullscreencontainer

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import top.fseasy.imlog.data.mapper.AbsolutePathModelParceler
import top.fseasy.imlog.data.mapper.MessageIdParceler
import top.fseasy.imlog.domain.model.AbsolutePathModel
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.features.home.topiclog.timeline.MessageContentUiModel
import top.fseasy.imlog.features.home.topiclog.timeline.MessageUiModel

/** For Full Screen show. */
@Immutable
@Parcelize
@TypeParceler<MessageId, MessageIdParceler>
sealed interface FullScreenContainerUiModel : Parcelable {
  val id: MessageId

  @Immutable
  @Parcelize
  @TypeParceler<AbsolutePathModel, AbsolutePathModelParceler>()
  data class ImageShow(
      val message: MessageUiModel<MessageContentUiModel.Image>,
      val path: AbsolutePathModel,
  ) : FullScreenContainerUiModel {
    override val id: MessageId
      get() = message.id
  }

  @Immutable
  @Parcelize
  @TypeParceler<AbsolutePathModel, AbsolutePathModelParceler>()
  data class VideoShow(
      val message: MessageUiModel<MessageContentUiModel.Video>,
      val path: AbsolutePathModel,
  ) : FullScreenContainerUiModel {
    override val id: MessageId
      get() = message.id
  }

  @Immutable
  @Parcelize
  data class TextSelection(
      val message: MessageUiModel<MessageContentUiModel.Text>,
  ) : FullScreenContainerUiModel {
    override val id: MessageId
      get() = message.id
  }
}
