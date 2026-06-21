package top.fseasy.imlog.features.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.fseasy.imlog.domain.model.HomeTopic
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.TopicRepository
import top.fseasy.imlog.domain.repository.UserRepository
import javax.inject.Inject


data class TopicsUiState(
    val isLoading: Boolean = true,
    val topics: List<HomeTopic> = emptyList(),
    val currentUserId: UserId? = null,
    val showCreateDialog: Boolean = false,
    val selectedTopicId: TopicId? = null,
)

@HiltViewModel
class TopicsViewModel @Inject constructor(
    private val topicRepository: TopicRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _showCreateDialog = MutableStateFlow(false)
    private val _selectedTopicId = MutableStateFlow<TopicId?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TopicsUiState> = userRepository.observeUserIdOrNull
        .distinctUntilChanged()
        .flatMapLatest { uId ->
            if (uId == null) {
                flowOf(TopicsUiState())
            } else {
                combine(
                    topicRepository.observeHomeTopics(uId),
                    _showCreateDialog,
                    _selectedTopicId
                ) { topics, showDialog, selectedId ->
                    TopicsUiState(
                        isLoading = false,
                        topics = topics,
                        currentUserId = uId,
                        showCreateDialog = showDialog,
                        selectedTopicId = selectedId
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TopicsUiState()
        )

    fun showCreateDialog() {
        _showCreateDialog.value = true
    }

    fun hideCreateDialog() {
        _showCreateDialog.value = false
    }

    fun selectTopic(topicId: TopicId?) {
        _selectedTopicId.value = topicId
    }

    fun createTopic(name: String) {
        viewModelScope.launch {
            val userId = uiState.value.currentUserId ?: return@launch
            topicRepository.createTopic(userId, name = name, iconUri = null)
            _showCreateDialog.value = false
        }
    }

    fun pinTopic(topicId: TopicId) {
        viewModelScope.launch {
            val userId = uiState.value.currentUserId ?: return@launch
            val topic = uiState.value.topics.find { it.id == topicId } ?: return@launch
            topicRepository.pinTopic(userId = userId, topicId = topicId, pinned = !topic.isPinned)
            _selectedTopicId.value = null
        }
    }

    fun deleteTopic(topicId: TopicId) {
        viewModelScope.launch {
            val userId = uiState.value.currentUserId ?: return@launch
            topicRepository.deleteTopic(userId = userId, topicId = topicId)
            _selectedTopicId.value = null
        }
    }

    fun archiveTopic(topicId: TopicId) {
        viewModelScope.launch {
            val userId = uiState.value.currentUserId ?: return@launch
            topicRepository.archiveTopic(userId = userId, topicId = topicId, archived = true)
            _selectedTopicId.value = null
        }
    }
}
