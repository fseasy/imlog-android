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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import top.fseasy.imlog.domain.model.AuthState
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.repository.MessageRepository
import top.fseasy.imlog.domain.repository.UserRepository
import top.fseasy.imlog.domain.usecase.StoragePathUseCase
import top.fseasy.imlog.navigation.MainScreen
import javax.inject.Inject

@HiltViewModel
class MessageTimelineViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val storagePathUseCase: StoragePathUseCase,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
  val topicId: TopicId = savedStateHandle.toRoute<MainScreen.TopicTimeline>().topicId

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
}
