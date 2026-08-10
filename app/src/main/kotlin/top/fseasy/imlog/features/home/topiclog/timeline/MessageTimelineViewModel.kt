package top.fseasy.imlog.features.home.topiclog.timeline

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import top.fseasy.imlog.R
import top.fseasy.imlog.domain.model.AuthState
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.Topic
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.MessageRepository
import top.fseasy.imlog.domain.repository.StorageRepository
import top.fseasy.imlog.domain.repository.TopicRepository
import top.fseasy.imlog.domain.repository.UserRepository
import top.fseasy.imlog.domain.usecase.StoragePathUseCase
import top.fseasy.imlog.domain.util.runSuspendCatching
import top.fseasy.imlog.navigation.MainScreen
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

sealed interface ContextState {
  object Loading : ContextState

  data class Error(val reason: String) : ContextState

  data class Success(
      val topic: Topic,
      val currentUserId: UserId,
  ) : ContextState
}

sealed interface MessageTimelineUiEffect {
  data class ShowSnackBar(val message: String) : MessageTimelineUiEffect

  data class OpenFileChooser(
      val uri: android.net.Uri,
      val mimeType: String?,
      val displayName: String,
  ) : MessageTimelineUiEffect
}

@HiltViewModel
class MessageTimelineViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val userRepository: UserRepository,
    private val topicRepository: TopicRepository,
    private val messageRepository: MessageRepository,
    private val storagePathUseCase: StoragePathUseCase,
    private val storageRepository: StorageRepository,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

  val topicId: TopicId = savedStateHandle.toRoute<MainScreen.TopicTimeline>().topicId
  private val _uiEffect = Channel<MessageTimelineUiEffect>()
  val uiEffect = _uiEffect.receiveAsFlow()

  // For audio/voice message playing position cache
  private val playPositionCache = mutableMapOf<MessageId, Duration>()
  private val audioPlayer =
      AudioPlayerStateHolder(
              context = context,
              onPlayingStopped = { state, playPosition ->
                state.playingMessageId?.let { playPositionCache[it] = playPosition }
              },
          )
          .also(::addCloseable)
  val audioPlaybackState = audioPlayer.playbackState
  val audioPlayPosition = audioPlayer.playPositionState

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

  @OptIn(ExperimentalCoroutinesApi::class)
  val pagedMessagesStateFlow: Flow<PagingData<MessageUiModel>> =
      userRepository.authState.filterIsInstance<AuthState.Authenticated>().flatMapLatest { state ->
        messageRepository
            .pagedTopicMessages(topicId)
            .map { pagingData ->
              pagingData.map { timelineMessage ->
                timelineMessage.toUiModel(
                    signInUserId = state.userId,
                    topicId = topicId,
                    storagePathUseCase = storagePathUseCase,
                    context = context,
                )
              }
            }
            .cachedIn(viewModelScope)
      }

  fun getCachedPlayPosition(messageId: MessageId) = playPositionCache[messageId] ?: 0.milliseconds

  fun changeAudioPlaybackSpeed(messageId: MessageId) = viewModelScope.launch {
    audioPlayer.changeSpeed(messageId)
  }

  /** Build AudioInput without cache as the db data may change */
  fun seekAudio(message: MessageUiModel, ratio: Float) {
    when (val content = message.content) {
      is MessageContentUiModel.AudioPlaySupported ->
          launchWithUserId { userId ->
            val uri =
                runSuspendCatching {
                  content.buildUri(
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
                          MessageTimelineUiEffect.ShowSnackBar(
                              context.getString(R.string.home_timeline_audio_not_found)
                          )
                      )
                      return@launchWithUserId
                    }
            val audioInput =
                AudioInput(
                    id = message.id,
                    uri = uri,
                    fileDuration = content.duration,
                )
            audioPlayer.seekToRatio(audioInput, ratio = ratio)
          }
      else -> Timber.w("Call seek audio for content-type: ${message.content::class.qualifiedName}")
    }
  }

  /** Build AudioInput without cache as the db data may change */
  fun toggleAudioPlay(message: MessageUiModel) {
    when (val content = message.content) {
      is MessageContentUiModel.AudioPlaySupported ->
          launchWithUserId { userId ->
            val uri =
                runSuspendCatching {
                  content.buildUri(
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
                          MessageTimelineUiEffect.ShowSnackBar(
                              context.getString(R.string.home_timeline_audio_not_found)
                          )
                      )
                      return@launchWithUserId
                    }

            audioPlayer.togglePlayPause(
                AudioInput(
                    id = message.id,
                    uri = uri,
                    fileDuration = content.duration,
                )
            )
          }
      else -> Timber.w("Call seek audio for content-type: ${message.content::class.qualifiedName}")
    }
  }

  fun onFileClicked(messageId: String) {
    viewModelScope.launch {
      // 1. 在协程中查询 SAF URI (例如查数据库或本地存储)
      val fileEntity = fileRepository.querySafUriByMessageId(messageId)

      if (fileEntity != null && fileEntity.safUri != null) {
        // 2. 查询成功，抛出打开文件的 UI 事件
        _uiEvent.send(
            ChatUiEvent.OpenFileChooser(
                uri = fileEntity.safUri,
                mimeType = fileEntity.mimeType,
                filename = fileEntity.displayFilename,
            )
        )
      } else {
        // 查询失败处理
        _uiEvent.send(ChatUiEvent.ShowToast("本地文件不存在或已被删除"))
      }
    }
  }

  private fun launchWithUserId(block: suspend (userId: UserId) -> Unit) {
    (userRepository.authState.value as? AuthState.Authenticated)?.userId?.let { uid ->
      viewModelScope.launch { block(uid) }
    }
  }
}
