package top.fseasy.imtrace.app.ui.topics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import top.fseasy.imtrace.app.data.repository.TopicRepository
import top.fseasy.imtrace.app.data.repository.UserRepository
import top.fseasy.imtrace.app.domain.model.Topic
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TopicsUiState(
    val isLoading: Boolean = true,
    val topics: List<Topic> = emptyList(),
    val currentUserId: String? = null,
    val showCreateDialog: Boolean = false,
    val selectedTopicId: String? = null
)

@HiltViewModel
class TopicsViewModel @Inject constructor(
    private val topicRepository: TopicRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _showCreateDialog = MutableStateFlow(false)
    private val _selectedTopicId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<TopicsUiState> = combine(
        topicRepository.getTopics(),
        userRepository.currentUserId,
        _showCreateDialog,
        _selectedTopicId
    ) { topics, userId, showDialog, selectedId ->
        TopicsUiState(
            isLoading = false,
            topics = topics.filter { !it.isArchived }
                .sortedByDescending { it.isPinned }
                .sortedByDescending { it.createdAt },
            currentUserId = userId,
            showCreateDialog = showDialog,
            selectedTopicId = selectedId
        )
    }.stateIn(
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

    fun selectTopic(topicId: String?) {
        _selectedTopicId.value = topicId
    }

    fun createTopic(name: String) {
        viewModelScope.launch {
            val userId = userRepository.currentUserId.first() ?: return@launch
            topicRepository.createTopic(name, userId)
            _showCreateDialog.value = false
        }
    }

    fun pinTopic(topicId: String) {
        viewModelScope.launch {
            val userId = userRepository.currentUserId.first() ?: return@launch
            val topic = uiState.value.topics.find { it.id == topicId } ?: return@launch
            topicRepository.pinTopic(topicId, userId, !topic.isPinned)
            _selectedTopicId.value = null
        }
    }

    fun deleteTopic(topicId: String) {
        viewModelScope.launch {
            topicRepository.deleteTopic(topicId)
            _selectedTopicId.value = null
        }
    }

    fun archiveTopic(topicId: String) {
        viewModelScope.launch {
            val userId = userRepository.currentUserId.first() ?: return@launch
            topicRepository.archiveTopic(topicId, userId, true)
            _selectedTopicId.value = null
        }
    }
}
