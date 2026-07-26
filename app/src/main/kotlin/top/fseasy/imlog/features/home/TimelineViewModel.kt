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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.fseasy.imlog.domain.model.Message
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.Topic
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.model.VoiceRecordingState
import top.fseasy.imlog.domain.repository.MessageRepository
import top.fseasy.imlog.domain.repository.TopicRepository
import top.fseasy.imlog.domain.repository.UserRepository
import top.fseasy.imlog.domain.usecase.StoragePathUseCase
import top.fseasy.imlog.navigation.MainScreen
import javax.inject.Inject

data class MessageUiState(
    val message: Message,
    val thumbnailModel: ResourceModel?,
)

sealed interface ContentUiState {
    object Loading : ContentUiState

    data class Error(val reason: String) : ContentUiState

    data class Success(
        val topic: Topic,
        val currentUserId: UserId,
        val messages: List<MessageUiState> = emptyList(),
    ) : ContentUiState
}

data class VoiceRecordingUiState(
    val voiceRecordingState: VoiceRecordingState = VoiceRecordingState.Idle,
    val elapsedMs: Long = 0,
)


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
    val contentUiState: StateFlow<ContentUiState> = userRepository.observeCurrentUserIdOrNull()
        .filterNotNull()
        .distinctUntilChanged()
        .flatMapLatest { uid ->
            combine(
                topicRepository.observeTopic(topicId),
                messageRepository.observeTopicMessages(topicId, uid),
            ) { topic, messages ->
                when (topic) {
                    null -> ContentUiState.Error("Failed to load Topic for id: $topicId")
                    else -> ContentUiState.Success(
                        currentUserId = uid,
                        topic = topic,
                        messages = messages.map { m ->
                            MessageUiState(
                                message = m, thumbnailModel = buildThumbnailModel(m, uid)
                            )
                        },
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ContentUiState.Loading
        )

    fun copyMessage(message: Message) {
        // TODO: 实现剪贴板复制
    }


    private fun buildThumbnailModel(
        message: Message,
        userId: UserId,
    ): ResourceModel? {
        if (message.type != MessageType.Image && message.type != MessageType.Video) {
            return null
        }
        return null
    }


}