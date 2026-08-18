package top.fseasy.imlog.features.home.topiclog

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import top.fseasy.imlog.R
import top.fseasy.imlog.data.mapper.toUriStr
import top.fseasy.imlog.data.util.ExoPlayerStateHolder
import top.fseasy.imlog.data.util.MediaInput
import top.fseasy.imlog.data.util.MediaPlaybackState
import top.fseasy.imlog.domain.model.AbsolutePathModel
import top.fseasy.imlog.domain.model.AuthState
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.Topic
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.StorageRepository
import top.fseasy.imlog.domain.repository.TopicRepository
import top.fseasy.imlog.domain.repository.UserRepository
import top.fseasy.imlog.domain.usecase.StoragePathUseCase
import top.fseasy.imlog.domain.util.runSuspendCatching
import top.fseasy.imlog.features.home.topiclog.timeline.FullScreenMessageUiModel
import top.fseasy.imlog.features.home.topiclog.timeline.MessageContentUiModel
import top.fseasy.imlog.features.home.topiclog.timeline.MessageUiModel
import top.fseasy.imlog.features.home.topiclog.timeline.buildFileUri
import top.fseasy.imlog.navigation.MainScreen
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

sealed interface ContextState {
  object Loading : ContextState

  data class Error(val reason: String) : ContextState

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

  data class SetFullScreenViewMessage(val fullScreenMessage: FullScreenMessageUiModel) :
      TopicLogUiEffect
}

@Immutable
data class MediaPlaybackStateAndAction(
    val activePlaybackStateHolder: State<MediaPlaybackState>,
    val activePlayPositionHolder: State<kotlin.time.Duration>,
    val inactivePlayPositionGetter: (MessageId) -> kotlin.time.Duration,
    val onTogglePlay: (MessageUiModel) -> Unit,
    val onSeek: (MessageUiModel, ratio: Float) -> Unit,
    val onCyclePlaybackSpeed: (MessageId) -> Unit,
)

/**
 * Read `.activePlaybackStateHolder.value` and `.inactivePlayPositionGetter` of
 * MediaPlaybackStateAndAction, then prepares a PlaybackState based on the current message isActive
 * state.
 *
 * When target change, those will be re-composition.
 */
@Composable
fun ReadMediaPlaybackStateAndRender(
    mediaPlaybackStateAndAction: MediaPlaybackStateAndAction,
    messageId: MessageId,
    messageContent: MessageContentUiModel.AudioPlaySupported,
    renderContent:
        @Composable
        (
            currentPlaybackState: MediaPlaybackState,
            inactivePlayPosition: Duration,
        ) -> Unit,
) {
  val audioPlaybackState = mediaPlaybackStateAndAction.activePlaybackStateHolder.value
  val isActive = audioPlaybackState.isThisMediaActive(toMediaInputId(messageId))
  val currentPlaybackState =
      if (isActive) {
        audioPlaybackState
      } else {
        MediaPlaybackState(duration = messageContent.duration)
      }
  // it will be recorded before switching to next one
  val inactivePlayPosition = mediaPlaybackStateAndAction.inactivePlayPositionGetter(messageId)
  // Render bubble
  renderContent(currentPlaybackState, inactivePlayPosition)
}

@HiltViewModel
class TopicLogViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    topicRepository: TopicRepository,
    private val userRepository: UserRepository,
    private val storagePathUseCase: StoragePathUseCase,
    private val storageRepository: StorageRepository,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

  val topicId: TopicId = TopicId(savedStateHandle.toRoute<MainScreen.TopicTimeline>().topicId)
  private val _uiEffect = Channel<TopicLogUiEffect>()
  val uiEffect = _uiEffect.receiveAsFlow()

  // For audio/voice message playing position cache
  private val inactiveMediaPlayPositionCache = mutableMapOf<String, kotlin.time.Duration>()
  private val exoPlayerStateHolder =
      ExoPlayerStateHolder(
              context = context,
              onPlayingStopped = { state, playPosition ->
                val playingId = state.playingId ?: return@ExoPlayerStateHolder
                val totalDuration = state.duration
                if (totalDuration < 1.minutes) return@ExoPlayerStateHolder
                if (totalDuration - playPosition < 10.seconds) {
                  // play done. REMOVE
                  inactiveMediaPlayPositionCache.remove(playingId)
                } else {
                  inactiveMediaPlayPositionCache[playingId] = playPosition
                }
              },
          )
          .also(::addCloseable)

  /** Playback State/Position are global state! which means, there is at most 1 playing media */
  val activeMediaPlaybackState = exoPlayerStateHolder.playbackState
  val activeMediaPlayPosition = exoPlayerStateHolder.playPositionState
  val player = exoPlayerStateHolder.exoPlayer

  @OptIn(ExperimentalCoroutinesApi::class)
  val contextStateFlow: StateFlow<ContextState> =
      combine(
              userRepository.authState.filterIsInstance<AuthState.Authenticated>(),
              topicRepository.observeTopicOrNull(topicId),
          ) { authState, topic ->
            when (topic) {
              null -> ContextState.Error("Failed to load Topic for id: $topicId")
              else ->
                  ContextState.Success(
                      currentUserId = authState.userId,
                      topic = topic,
                  )
            }
          }
          .stateIn(
              scope = viewModelScope,
              started = SharingStarted.WhileSubscribed(5000),
              initialValue = ContextState.Loading,
          )

  fun getMediaCachedPlayPosition(messageId: MessageId) =
      inactiveMediaPlayPositionCache[toMediaInputId(messageId)] ?: 0.milliseconds

  /** Cycle Playback Speed in range of {1, 1.5, 2}. Only work when trigger id == playing id */
  fun cycleMediaPlaybackSpeed(triggerMessageId: MessageId) {
    if (toMediaInputId(triggerMessageId) != activeMediaPlaybackState.value.playingId) return
    val newSpeed =
        when (activeMediaPlaybackState.value.speed) {
          1.0f -> 1.5f
          1.5f -> 2.0f
          else -> 1.0f
        }
    viewModelScope.launch { exoPlayerStateHolder.changeSpeed(newSpeed) }
  }

  /** Build MediaInput without cache as the db data may change */
  fun seekMedia(message: MessageUiModel, ratio: Float) {
    val content =
        message.content as? MessageContentUiModel.AudioPlaySupported
            ?: run {
              Timber.w("Call seek audio for content-type: ${message.content::class.qualifiedName}")
              return
            }

    launchWithUserId { userId ->
      val uri =
          runSuspendCatching {
            content.buildFileUri(
                signInUserId = userId,
                topicId = topicId,
                messageCreatedAt = message.createdAt,
                audioSupportedContent = content,
                storagePathUseCase = storagePathUseCase,
                storageRepository = storageRepository,
            )
          }
              .getOrNull()
              ?: run {
                _uiEffect.send(
                    TopicLogUiEffect.ShowSnackBar(
                        context.getString(R.string.term_media_file_not_found)
                    )
                )
                return@launchWithUserId
              }
      val input =
          MediaInput(
              id = toMediaInputId(message.id),
              uri = uri,
              fileDuration = content.duration,
          )
      exoPlayerStateHolder.seekToRatio(input, ratio = ratio)
    }
  }

  /** Build MediaInput without cache as the db data may change */
  fun toggleMediaPlay(message: MessageUiModel) {
    val content =
        message.content as? MessageContentUiModel.AudioPlaySupported
            ?: run {
              Timber.w(
                  "Call play/pause audio for content-type: ${message.content::class.qualifiedName}"
              )
              return
            }

    launchWithUserId { userId ->
      val uri =
          runSuspendCatching {
            content.buildFileUri(
                signInUserId = userId,
                topicId = topicId,
                messageCreatedAt = message.createdAt,
                audioSupportedContent = content,
                storagePathUseCase = storagePathUseCase,
                storageRepository = storageRepository,
            )
          }
              .getOrNull()
              ?: run {
                _uiEffect.send(
                    TopicLogUiEffect.ShowSnackBar(
                        context.getString(R.string.term_media_file_not_found)
                    )
                )
                return@launchWithUserId
              }

      exoPlayerStateHolder.togglePlayPause(
          MediaInput(
              id = toMediaInputId(message.id),
              uri = uri,
              fileDuration = content.duration,
          )
      )
    }
  }

  /** Create the OpenFileChoose intent for GenericFile Message. Send UI Effect when success */
  fun createOpenFileIntentForGenericFileMessage(message: MessageUiModel) {
    val fileMessageContent =
        message.content as? MessageContentUiModel.GenericFile
            ?: run {
              Timber.w("Invalid message content when call FileClicked: $message")
              return
            }
    launchWithUserId { userId ->
      val uri =
          runSuspendCatching {
            fileMessageContent.buildFileUri(
                signInUserId = userId,
                topicId = topicId,
                messageCreatedAt = message.createdAt,
                storagePathUseCase = storagePathUseCase,
                storageRepository = storageRepository,
            )
          }
              .onFailure { e -> Timber.w(e, "Generic File build storage uri failed") }
              .getOrNull()
              ?: run {
                _uiEffect.send(
                    TopicLogUiEffect.ShowSnackBar(
                        "Failed to resolve file: ${fileMessageContent.displayFilename}"
                    )
                )
                return@launchWithUserId
              }
      _uiEffect.send(
          TopicLogUiEffect.OpenFileChooser(
              uri = uri,
              mimeType = fileMessageContent.mimeType,
              displayName = fileMessageContent.displayFilename,
          )
      )
    }
  }

  fun prepareFullScreenViewMessage(message: MessageUiModel): Unit {
    launchWithUserId { userId ->
      // currently fullscreen only support Image/Video
      val content = message.content as? MessageContentUiModel.ImageLike ?: return@launchWithUserId
      val uri =
          runSuspendCatching {
            content.buildFileUri(
                signInUserId = userId,
                topicId = topicId,
                messageCreatedAt = message.createdAt,
                storagePathUseCase = storagePathUseCase,
                storageRepository = storageRepository,
            )
          }
              .onFailure { e -> Timber.w(e, "FullScreenView build storage uri failed") }
              .getOrNull()
              ?: run {
                _uiEffect.send(TopicLogUiEffect.ShowSnackBar("Failed to resolve file"))
                return@launchWithUserId
              }
      val path = AbsolutePathModel.UriStrModel(uri.toUriStr())
      val model = FullScreenMessageUiModel(message = message, path = path)
      _uiEffect.send(TopicLogUiEffect.SetFullScreenViewMessage(model))
    }
  }

  private fun launchWithUserId(block: suspend (userId: UserId) -> Unit) {
    (userRepository.authState.value as? AuthState.Authenticated)?.userId?.let { uid ->
      viewModelScope.launch { block(uid) }
    }
  }
}
