package top.fseasy.imlog.features.home.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.fseasy.imlog.domain.model.AuthState
import top.fseasy.imlog.domain.model.HomeTopic
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.TopicRepository
import top.fseasy.imlog.domain.repository.UserRepository
import javax.inject.Inject

data class TopicsUiState(
    val loading: Boolean = true,
    val topics: List<HomeTopic> = emptyList(),
)

@HiltViewModel
class TopicListViewModel @Inject constructor(
    userRepository: UserRepository,
    private val topicRepository: TopicRepository,
) : ViewModel() {

    private val _authStateFlow = userRepository.authState

    @OptIn(ExperimentalCoroutinesApi::class)
    val topicsUiStateFlow: StateFlow<TopicsUiState> =
        _authStateFlow.filterIsInstance<AuthState.Authenticated>()
            .flatMapLatest { state ->
                topicRepository.observeHomeTopics(state.userId)
                    .map { topics ->
                        TopicsUiState(loading = false, topics = topics)
                    }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = TopicsUiState()
            )

    fun pinTopic(topicId: TopicId) {
        launchWithUid { userId ->
            val topic =
                topicsUiStateFlow.value.topics.find { it.id == topicId } ?: return@launchWithUid
            topicRepository.pinTopic(
                userId = userId, topicId = topicId, pinned = !topic.isPinned
            )
        }
    }

    fun deleteTopic(topicId: TopicId) {
        launchWithUid { userId ->
            topicRepository.deleteTopic(userId = userId, topicId = topicId)
        }
    }

    fun archiveTopic(topicId: TopicId) {
        launchWithUid { userId ->
            topicRepository.archiveTopic(
                userId = userId, topicId = topicId, archived = true
            )
        }
    }

    private fun launchWithUid(block: suspend (UserId) -> Unit) {
        val uid = (_authStateFlow.value as? AuthState.Authenticated)?.userId ?: return
        viewModelScope.launch { block(uid) }
    }
}