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
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import top.fseasy.imlog.domain.model.AuthState
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.Topic
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.MessageRepository
import top.fseasy.imlog.domain.repository.TopicRepository
import top.fseasy.imlog.domain.repository.UserRepository
import top.fseasy.imlog.domain.usecase.StoragePathUseCase
import top.fseasy.imlog.navigation.MainScreen

sealed interface ContextState {
  object Loading : ContextState

  data class Error(val reason: String) : ContextState

  data class Success(
      val topic: Topic,
      val currentUserId: UserId,
  ) : ContextState
}

@HiltViewModel
class MessageTimelineViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    userRepository: UserRepository,
    private val topicRepository: TopicRepository,
    private val messageRepository: MessageRepository,
    private val storagePathUseCase: StoragePathUseCase,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

  val topicId: TopicId = savedStateHandle.toRoute<MainScreen.TopicTimeline>().topicId

  // For audio/voice message playing position cache
  private val playPositionCache = mutableMapOf<MessageId, kotlin.time.Duration>()
  val audioPlayer =
      AudioPlayerStateHolder(
              context = context,
              onPlayingStopped = { state, playPosition ->
                state.playingMessageId?.let { playPositionCache[it] = playPosition }
              },
          )
          .also(::addCloseable)

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
}
