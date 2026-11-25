package top.fseasy.imlog.features.home.conversation

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import kotlin.time.Duration
import top.fseasy.imlog.data.util.MediaPlaybackState
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.StoragePathModel
import top.fseasy.imlog.domain.model.Topic
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.usecase.composer.DraftRecoveryNotice
import top.fseasy.imlog.features.home.conversation.composer2.voice.VoiceRecordingNotice
import top.fseasy.imlog.features.home.conversation.fullscreencontainer.FullScreenContainerUiModel
import top.fseasy.imlog.features.home.conversation.timeline.MessageContentUiModel
import top.fseasy.imlog.features.home.conversation.timeline.MessageUiModel

sealed interface ContextState {
  @Immutable object Loading : ContextState

  @Immutable data class Error(val reason: String) : ContextState

  @Immutable
  data class Success(
      val topic: Topic,
      val currentUserId: UserId,
  ) : ContextState
}

sealed interface GeneralNotice {
  data object FailedToResolveFile : GeneralNotice
}

sealed interface ConversationUiEvent {
  data class ShowVoiceRecordingNotice(val notice: VoiceRecordingNotice) : ConversationUiEvent

  data class ShowVoiceDraftRecoveryNotice(val notice: DraftRecoveryNotice) : ConversationUiEvent

  data class ShowGeneralNotice(val notice: GeneralNotice) : ConversationUiEvent

  data class OpenSharedFile(val path: StoragePathModel.Shared) : ConversationUiEvent

  data class SetFullScreenViewMessage(val fullScreenMessage: FullScreenContainerUiModel) :
      ConversationUiEvent
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
