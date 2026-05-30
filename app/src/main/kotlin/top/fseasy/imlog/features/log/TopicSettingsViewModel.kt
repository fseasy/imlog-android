package top.fseasy.imlog.features.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import top.fseasy.imlog.data.repository.TopicRepositoryImpl
import top.fseasy.imlog.data.repository.UserRepository
import top.fseasy.imlog.domain.model.Topic
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TopicSettingsUiState(
    val isLoading: Boolean = true,
    val topic: Topic? = null
)

@HiltViewModel
class TopicSettingsViewModel @Inject constructor(
    private val topicRepositoryImpl: TopicRepositoryImpl,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _topicId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<TopicSettingsUiState> = _topicId.flatMapLatest { id ->
        if (id != null) topicRepositoryImpl.getTopic(id)
        else flowOf(null)
    }.combine(MutableStateFlow(false)) { topic, _ ->
        TopicSettingsUiState(
            isLoading = false,
            topic = topic
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TopicSettingsUiState()
    )

    fun loadTopic(topicId: String) {
        _topicId.value = topicId
    }

    fun updateTopicName(name: String) {
        viewModelScope.launch {
            val topicId = _topicId.value ?: return@launch
            topicRepositoryImpl.updateTopic(topicId, name, null)
        }
    }

    fun togglePin() {
        viewModelScope.launch {
            val topicId = _topicId.value ?: return@launch
            val userId = userRepository.currentUserId.first() ?: return@launch
            val topic = uiState.value.topic ?: return@launch
            topicRepositoryImpl.pinTopic(topicId, userId, !topic.isPinned)
        }
    }

    fun toggleArchive() {
        viewModelScope.launch {
            val topicId = _topicId.value ?: return@launch
            val userId = userRepository.currentUserId.first() ?: return@launch
            val topic = uiState.value.topic ?: return@launch
            topicRepositoryImpl.archiveTopic(topicId, userId, !topic.isArchived)
        }
    }

    fun deleteTopic() {
        viewModelScope.launch {
            val topicId = _topicId.value ?: return@launch
            topicRepositoryImpl.deleteTopic(topicId)
        }
    }

    fun setFont(font: String) {
        viewModelScope.launch {
            val topicId = _topicId.value ?: return@launch
            topicRepositoryImpl.setTopicFont(topicId, font)
        }
    }
}
