package top.fseasy.imlog.features.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import top.fseasy.imlog.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.TopicWithPersonalState
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.TopicRepository
import javax.inject.Inject

data class TopicSettingsUiState(
    val isLoading: Boolean = true,
    val topicWithPersonalState: TopicWithPersonalState? = null,
)

@HiltViewModel
class TopicSettingsViewModel @Inject constructor(
    private val topicRepository: TopicRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _topicId = MutableStateFlow<TopicId?>(null)
    private val _currentUserId: StateFlow<UserId?> = userRepository.observeUserIdOrNull.stateIn(
        scope = viewModelScope, started = SharingStarted.WhileSubscribed(2000), initialValue = null
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TopicSettingsUiState> = combine(
        _topicId, _currentUserId
    ) { tid, uid -> tid to uid }.distinctUntilChanged()
        .flatMapLatest { (tid, uid) ->
            if (tid == null || uid == null) {
                flowOf(TopicSettingsUiState())
            } else {
                topicRepository.observeTopicWithPersonalState(tid, uid)
                    .map { t ->
                        TopicSettingsUiState(
                            isLoading = false, topicWithPersonalState = t
                        )
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TopicSettingsUiState(isLoading = true)
        )

    fun loadTopic(topicId: TopicId) {
        _topicId.value = topicId
    }

    fun updateTopicName(name: String) {
        viewModelScope.launch {
            val userId = requireCurrentUserId()
            val topicId = _topicId.value ?: return@launch
            topicRepository.updateTopicName(userId = userId, topicId = topicId, newName = name)
        }
    }

    fun togglePin() {
        viewModelScope.launch {
            val userId = requireCurrentUserId()
            val topicId = _topicId.value ?: return@launch
            val topic = uiState.value.topicWithPersonalState ?: return@launch
            topicRepository.pinTopic(userId = userId, topicId = topicId, pinned = !topic.isPinned)
        }
    }

    fun toggleArchive() {
        viewModelScope.launch {
            val topicId = _topicId.value ?: return@launch
            val userId = requireCurrentUserId()
            val topic = uiState.value.topicWithPersonalState ?: return@launch
            topicRepository.archiveTopic(
                userId = userId,
                topicId = topicId,
                archived = !topic.isArchived
            )
        }
    }

    fun deleteTopic() {
        viewModelScope.launch {
            val userId = requireCurrentUserId()
            val topicId = _topicId.value ?: return@launch
            topicRepository.deleteTopic(userId = userId, topicId = topicId)
        }
    }

    private suspend fun requireCurrentUserId(): UserId {
        return _currentUserId.firstOrNull() ?: error("未登录")
    }
}
