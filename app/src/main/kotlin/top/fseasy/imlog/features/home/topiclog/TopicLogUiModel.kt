package top.fseasy.imlog.features.home.topiclog

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import top.fseasy.imlog.data.util.MediaPlaybackState
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.Topic
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.features.home.topiclog.fullscreencontainer.FullScreenContainerUiModel
import top.fseasy.imlog.features.home.topiclog.timeline.MessageContentUiModel
import top.fseasy.imlog.features.home.topiclog.timeline.MessageUiModel
import kotlin.time.Duration

sealed interface ContextState {
  @Immutable object Loading : ContextState

  @Immutable data class Error(val reason: String) : ContextState

  @Immutable
  data class Success(
      val topic: Topic,
      val currentUserId: UserId,
  ) : ContextState
}

sealed interface TopicLogUiEffect {
  data class ShowSnackBar(val message: String) : TopicLogUiEffect

  data class OpenFileChooser(
      val uri: Uri,
      val mimeType: String?,
      val displayName: String,
  ) : TopicLogUiEffect

  data class SetFullScreenViewMessage(val fullScreenMessage: FullScreenContainerUiModel) :
      TopicLogUiEffect
}

@Immutable
data class MediaPlaybackStateAndAction(
    val activePlaybackStateHolder: State<MediaPlaybackState>,
    val activePlayPositionHolder: State<Duration>,
    val inactivePlayPositionGetter: (MessageId) -> Duration,
    val onTogglePlay: (MessageUiModel<MessageContentUiModel.AudioPlaySupported>) -> Unit,
    val onSeek: (MessageUiModel<MessageContentUiModel.AudioPlaySupported>, ratio: Float) -> Unit,
    val onCyclePlaybackSpeed: (MessageId) -> Unit,
)

@Immutable
data class ShowFullScreenMessageUiModelAction(
    val showImage: (MessageUiModel<MessageContentUiModel.Image>) -> Unit,
    val showVideo: (MessageUiModel<MessageContentUiModel.Video>) -> Unit,
    val showTextSelection: (MessageUiModel<MessageContentUiModel.Text>) -> Unit,
)
