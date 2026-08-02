package top.fseasy.imlog.features.home.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.fseasy.imlog.domain.model.AuthState
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.TopicRepository
import top.fseasy.imlog.domain.repository.UserRepository
import top.fseasy.imlog.domain.usecase.StoragePathUseCase
import javax.inject.Inject

data class TopicsUiState(
    val loading: Boolean = true,
    val topics: List<HomeTopicUiModel> = emptyList(),
)

@HiltViewModel
class TopicListViewModel
@Inject
constructor(
    userRepository: UserRepository,
    storagePathUseCase: StoragePathUseCase,
    private val topicRepository: TopicRepository,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

  private val _authStateFlow = userRepository.authState

  @OptIn(ExperimentalCoroutinesApi::class)
  val topicsUiStateFlow: StateFlow<TopicsUiState> =
      _authStateFlow
          .filterIsInstance<AuthState.Authenticated>()
          .flatMapLatest { state ->
            topicRepository.observeHomeTopics(state.userId).map { topics ->
              TopicsUiState(
                  loading = false,
                  topics =
                      topics.map {
                        it.toUiModel(
                            currentUserId = state.userId,
                            storagePathUseCase = storagePathUseCase,
                            context = context,
                        )
                      },
              )
            }
          }
          .flowOn(Dispatchers.Default) // Cpu working for transform.
          .stateIn(
              scope = viewModelScope,
              started = SharingStarted.WhileSubscribed(5000),
              initialValue = TopicsUiState(),
          )

  fun pinTopic(topicId: TopicId, currentPinState: Boolean) {
    launchWithUid { userId ->
      topicRepository.pinTopic(
          userId = userId,
          topicId = topicId,
          pinned = !currentPinState,
      )
    }
  }

  fun deleteTopic(topicId: TopicId) {
    launchWithUid { userId ->
      topicRepository.deleteTopic(userId = userId, topicId = topicId)
    }
  }

  fun archiveTopic(topicId: TopicId, currentArchivedState: Boolean) {
    launchWithUid { userId ->
      topicRepository.archiveTopic(
          userId = userId,
          topicId = topicId,
          archived = !currentArchivedState,
      )
    }
  }

  private fun launchWithUid(block: suspend (UserId) -> Unit) {
    val uid = (_authStateFlow.value as? AuthState.Authenticated)?.userId ?: return
    viewModelScope.launch { block(uid) }
  }
}
