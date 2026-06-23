package top.fseasy.imlog.features.home

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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.fseasy.imlog.data.repository.FileManager
import top.fseasy.imlog.domain.model.Message
import top.fseasy.imlog.domain.model.MessageFactory
import top.fseasy.imlog.domain.model.MessageMediaCopySource
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.Topic
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.model.VoiceRecordingState
import top.fseasy.imlog.domain.repository.MessageRepository
import top.fseasy.imlog.domain.repository.TopicRepository
import top.fseasy.imlog.domain.repository.UserRepository
import top.fseasy.imlog.features.home.domain.VoiceRecorder
import java.io.File
import javax.inject.Inject

data class MessageUiState(
    val message: Message,
    val thumbnailModel: ResourceModel?,
)

data class TimelineUiState(
    val isLoading: Boolean = true,
    val topic: Topic? = null,
    val messages: List<MessageUiState> = emptyList(),
    val currentUserId: UserId? = null,
    val voiceRecordingState: VoiceRecordingState = VoiceRecordingState.IDLE,
    val voiceRecordingElapsed: Long = 0,
)

@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val topicRepository: TopicRepository,
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    private val fileManager: FileManager,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    private val _topicId = MutableStateFlow<TopicId?>(null)
    private val _currentUserId: StateFlow<UserId?> = userRepository.observeUserIdOrNull.stateIn(
        scope = viewModelScope, started = SharingStarted.WhileSubscribed(2000), initialValue = null
    )

    val voiceRecorder = VoiceRecorder(viewModelScope)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TimelineUiState> = combine(
        _topicId.filterNotNull(), _currentUserId.filterNotNull()
    ) { tid, uid ->
        tid to uid
    }.flatMapLatest { (tid, uid) ->
        combine(
            topicRepository.observeTopic(tid),
            messageRepository.observeTopicMessages(tid, uid),
            voiceRecorder.state,
            voiceRecorder.elapsedMs
        ) { topic, messages, voiceState, elapsed ->
            TimelineUiState(
                isLoading = false,
                topic = topic,
                messages = messages.map { m ->
                    MessageUiState(
                        message = m,
                        thumbnailModel = buildThumbnailModel(m, uid)
                    )
                },
                currentUserId = uid,
                voiceRecordingState = voiceState,
                voiceRecordingElapsed = elapsed,
            )
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TimelineUiState()
        )

    fun loadTopic(topicId: TopicId) {
        _topicId.value = topicId
    }

    fun setVoiceRecorderState(state: VoiceRecordingState) {
        when (state) {
            VoiceRecordingState.IDLE -> voiceRecorder.cancel()
            VoiceRecordingState.RECORDING -> voiceRecorder.start(context)
            VoiceRecordingState.STOPPED -> {
                voiceRecorder.stop()
                    ?.let { sendVoiceMessage(it) }
            }
        }
    }

    fun sendTextMessage(content: String) {
        launchWithTopicUserId { topicId, userId ->
            val now = System.currentTimeMillis()
            val textMsg =
                MessageFactory.createText(topicId, userId, content = content, timestampMs = now)
            messageRepository.saveTextMessage(textMsg)
        }
    }

    fun sendImageMessage(uri: Uri) {
        sendMediaMessage(MessageMediaCopySource.FromUri(uri), MessageType.IMAGE)
    }

    fun sendVideoMessage(uri: Uri) {
        sendMediaMessage(MessageMediaCopySource.FromUri(uri), MessageType.VIDEO)
    }

    fun sendAudioMessage(uri: Uri) {
        sendMediaMessage(MessageMediaCopySource.FromUri(uri), MessageType.VIDEO)
    }

    fun sendVoiceMessage(cacheVoiceFile: File) {
        sendMediaMessage(
            MessageMediaCopySource.FromFile(cacheVoiceFile, deleteOnCopySuccess = true),
            MessageType.VOICE
        )
    }

    fun copyMessage(message: Message) {
        // TODO: 实现剪贴板复制
    }

    override fun onCleared() {
        super.onCleared()
        voiceRecorder.cancel()
    }

    private fun sendMediaMessage(copySource: MessageMediaCopySource, type: MessageType) {
        launchWithTopicUserId { topicId, userId ->
            messageRepository.sendMediaMessage(
                topicId = topicId,
                senderId = userId,
                messageType = type,
                srcMediaCopySource = copySource
            )
        }
    }

    private fun buildThumbnailModel(
        message: Message,
        userId: UserId,
    ): ResourceModel? {
        if (message.type != MessageType.IMAGE && message.type != MessageType.VIDEO) {
            return null
        }
        return message.thumbnailName?.let { name ->
            ResourceModel.FromFile(
                fileManager.calcThumbnailFile(
                    userId = userId,
                    topicId = message.topicId,
                    messageTimestampMs = message.createdAt,
                    thumbnailFilename = name
                )
            )
        } ?: message.originalFileUri?.let { uri ->
            ResourceModel.FromUri(uri)
        }
    }

// --- 内部工具 ---

    /**
     * 在协程中确保 topicId 与 userId 非空后执行 [block]
     */
    private inline fun launchWithTopicUserId(crossinline block: suspend (topicId: TopicId, userId: UserId) -> Unit) {
        viewModelScope.launch {
            val topicId = _topicId.value ?: return@launch
            val userId = requireCurrentUserId()
            block(topicId, userId)
        }
    }

    private suspend fun requireCurrentUserId(): UserId {
        return userRepository.observeUserIdOrNull.firstOrNull() ?: error("未登录")
    }

}