package top.fseasy.imlog.features.log

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.fseasy.imlog.domain.model.Message
import top.fseasy.imlog.domain.model.MessageFactory
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.Topic
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.model.VoiceRecordingState
import top.fseasy.imlog.domain.repository.MessageRepository
import top.fseasy.imlog.domain.repository.TopicRepository
import top.fseasy.imlog.domain.repository.UserRepository
import top.fseasy.imlog.features.log.domain.VoiceRecorder
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class TimelineUiState(
    val isLoading: Boolean = true,
    val topic: Topic? = null,
    val messages: List<Message> = emptyList(),
    val currentUserId: UserId? = null,
    val voiceRecordingState: VoiceRecordingState = VoiceRecordingState.IDLE,
    val voiceRecordingElapsed: Long = 0,
)

@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val topicRepository: TopicRepository,
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _topicId = MutableStateFlow<TopicId?>(null)

    val voiceRecorder = VoiceRecorder()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TimelineUiState> = combine(
        _topicId.flatMapLatest {
            it?.let { topicRepository.observeTopicById(it) } ?: flowOf(null)
        },
        _topicId.flatMapLatest {
            it?.let { messageRepository.observeTopicMessages(it) } ?: flowOf(emptyList())
        },
        userRepository.observeUserId,
        voiceRecorder.state,
        voiceRecorder.elapsedMs
    ) { topic, messages, userId, voiceState, elapsed ->
        TimelineUiState(
            isLoading = false,
            topic = topic,
            messages = messages,
            currentUserId = userId,
            voiceRecordingState = voiceState,
            voiceRecordingElapsed = elapsed
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TimelineUiState()
    )

    fun loadTopic(topicId: TopicId) {
        _topicId.value = topicId
    }

    fun startRecording() {
        voiceRecorder.start(context)
    }

    fun stopRecording() {
        val file = voiceRecorder.stop()
        if (file != null) {
            sendAudioMessage(file)
        }
    }

    fun cancelRecording() {
        voiceRecorder.cancel()
    }

    // --- 消息发送 ---
    fun sendTextMessage(content: String) {
        launchWithTopic { topicId ->
            val userId = requireCurrentUserId()
            val textMsg = MessageFactory.createText(topicId, userId, content)
            messageRepository.save(textMsg)
        }
    }

    fun sendImageMessage(uri: Uri) {
        sendMediaMessage(uri, MessageType.IMAGE)
    }

    fun sendVideoMessage(uri: Uri) {
        sendMediaMessage(uri, MessageType.VIDEO)
    }

    fun sendAudioMessage(file: File) {
        launchWithTopic { topicId ->
            val userId = requireCurrentUserId()
            val duration = voiceRecorder.elapsedMs.value / 1000
            messageRepository.sendMediaMessage(
                topicId = topicId,
                senderId = userId,
                type = MessageType.AUDIO,
                filePath = file.absolutePath,
                fileSize = file.length(),
                duration = duration,
                thumbnailPath = null
            )
        }
    }

    fun copyMessage(message: Message) {
        // TODO: 实现剪贴板复制
    }

    override fun onCleared() {
        super.onCleared()
        voiceRecorder.cancel()
    }

    // --- 内部工具 ---

    /**
     * 在协程中确保 topicId 与 userId 非空后执行 [block]
     */
    private inline fun launchWithTopic(crossinline block: suspend (TopicId) -> Unit) {
        viewModelScope.launch {
            val topicId = _topicId.value ?: return@launch
            block(topicId)
        }
    }

    private suspend fun requireCurrentUserId(): UserId {
        return userRepository.observeUserId.first() ?: error("未登录")
    }

    private fun sendMediaMessage(uri: Uri, type: MessageType) {
        viewModelScope.launch {
            val topicId = _topicId.value ?: return@launch
            val userId = requireCurrentUserId()
            val file = copyUriToInternal(uri, type.name.lowercase(), type.name.lowercase())
            messageRepository.sendMediaMessage(
                topicId = topicId,
                senderId = userId,
                type = type,
                filePath = file.absolutePath,
                fileSize = file.length(),
                duration = null,
                thumbnailPath = null
            )
        }
    }
}