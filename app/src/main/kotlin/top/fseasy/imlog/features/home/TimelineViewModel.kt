package top.fseasy.imlog.features.home

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import top.fseasy.imlog.domain.model.Message
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.Topic
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.MessageRepository
import top.fseasy.imlog.domain.repository.TopicRepository
import top.fseasy.imlog.domain.repository.UserRepository
import top.fseasy.imlog.navigation.MainScreen
import javax.inject.Inject

sealed interface ContextState {
    object Loading : ContextState

    data class Error(val reason: String) : ContextState

    data class Success(
        val topic: Topic,
        val currentUserId: UserId,
    ) : ContextState
}


@HiltViewModel
class TimelineViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    userRepository: UserRepository,
    private val topicRepository: TopicRepository,
    private val messageRepository: MessageRepository,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    val topicId: TopicId = savedStateHandle.toRoute<MainScreen.TopicTimeline>().topicId

    @OptIn(ExperimentalCoroutinesApi::class)
    val contextStateFlow: StateFlow<ContextState> = combine(
        userRepository.observeCurrentUserIdOrNull()
            .filterNotNull(),
        topicRepository.observeTopic(topicId),
    ) { uid, topic ->
        when (topic) {
            null -> ContextState.Error("Failed to load Topic for id: $topicId")
            else -> ContextState.Success(
                currentUserId = uid,
                topic = topic,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ContextState.Loading
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val messagesStateFlow: StateFlow<List<Message>> =
        messageRepository.observeTopicMessages(topicId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    fun copyMessage(message: Message) {
        // TODO: 实现剪贴板复制
    }

}