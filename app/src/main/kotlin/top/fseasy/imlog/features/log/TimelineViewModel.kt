package top.fseasy.imlog.features.log

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import top.fseasy.imlog.data.repository.MessageRepository
import top.fseasy.imlog.domain.repository.TopicRepository
import top.fseasy.imlog.data.repository.UserRepository
import top.fseasy.imlog.domain.model.Message
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.Topic
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

enum class VoiceRecordingState {
    IDLE, RECORDING, STOPPED
}

data class TimelineUiState(
    val isLoading: Boolean = true,
    val topic: Topic? = null,
    val messages: List<Message> = emptyList(),
    val currentUserId: String? = null,
    val voiceRecordingState: VoiceRecordingState = VoiceRecordingState.IDLE,
    val voiceRecordingElapsed: Long = 0
)

@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val topicRepository: TopicRepository,
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _topicId = MutableStateFlow<String?>(null)
    private val _voiceRecordingState = MutableStateFlow(VoiceRecordingState.IDLE)
    private val _voiceRecordingElapsed = MutableStateFlow(0L)
    private var recordingTimerJob: Job? = null
    private var mediaRecorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TimelineUiState> = combine(
        _topicId.flatMapLatest { id ->
            if (id != null) topicRepository.observeTopicById(id)
            else flowOf(null)
        },
        _topicId.flatMapLatest { id ->
            if (id != null) messageRepository.getMessages(id)
            else flowOf(emptyList())
        },
        userRepository.currentUserId,
        _voiceRecordingState,
        _voiceRecordingElapsed
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

    fun loadTopic(topicId: String) {
        _topicId.value = topicId
    }

    fun setVoiceRecordingState(state: VoiceRecordingState) {
        when (state) {
            VoiceRecordingState.RECORDING -> {
                startRecording()
            }

            VoiceRecordingState.STOPPED -> {
                stopRecording(cancelled = false)
            }

            VoiceRecordingState.IDLE -> {
                if (_voiceRecordingState.value != VoiceRecordingState.IDLE) {
                    stopRecording(cancelled = true)
                }
            }
        }
        _voiceRecordingState.value = state
    }

    private fun startRecording() {
        try {
            val outputFile = File(context.cacheDir, "voice_${UUID.randomUUID()}.m4a")
            currentRecordingFile = outputFile

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            _voiceRecordingElapsed.value = 0
            recordingTimerJob = viewModelScope.launch {
                while (true) {
                    delay(100)
                    _voiceRecordingElapsed.value += 100
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _voiceRecordingState.value = VoiceRecordingState.IDLE
        }
    }

    private fun stopRecording(cancelled: Boolean) {
        recordingTimerJob?.cancel()
        recordingTimerJob = null

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaRecorder = null

        if (cancelled) {
            currentRecordingFile?.delete()
        }
        currentRecordingFile = null
        _voiceRecordingElapsed.value = 0
    }

    fun sendTextMessage(content: String) {
        viewModelScope.launch {
            val topicId = _topicId.value ?: return@launch
            val userId = userRepository.currentUserId.first() ?: return@launch
            messageRepository.sendTextMessage(topicId, userId, content)
        }
    }

    fun sendImageMessage(uri: Uri) {
        viewModelScope.launch {
            val topicId = _topicId.value ?: return@launch
            val userId = userRepository.currentUserId.first() ?: return@launch

            // Copy file to app storage
            val inputStream = context.contentResolver.openInputStream(uri)
            val file = File(context.filesDir, "images_${UUID.randomUUID()}")
            inputStream?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            messageRepository.sendMediaMessage(
                topicId = topicId,
                senderId = userId,
                type = MessageType.IMAGE,
                filePath = file.absolutePath,
                fileSize = file.length(),
                duration = null,
                thumbnailPath = null
            )
        }
    }

    fun sendVideoMessage(uri: Uri) {
        viewModelScope.launch {
            val topicId = _topicId.value ?: return@launch
            val userId = userRepository.currentUserId.first() ?: return@launch

            // Copy file to app storage
            val inputStream = context.contentResolver.openInputStream(uri)
            val file = File(context.filesDir, "videos_${UUID.randomUUID()}")
            inputStream?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            messageRepository.sendMediaMessage(
                topicId = topicId,
                senderId = userId,
                type = MessageType.VIDEO,
                filePath = file.absolutePath,
                fileSize = file.length(),
                duration = null,
                thumbnailPath = null
            )
        }
    }

    fun sendAudioMessage(file: File) {
        viewModelScope.launch {
            val topicId = _topicId.value ?: return@launch
            val userId = userRepository.currentUserId.first() ?: return@launch

            messageRepository.sendMediaMessage(
                topicId = topicId,
                senderId = userId,
                type = MessageType.AUDIO,
                filePath = file.absolutePath,
                fileSize = file.length(),
                duration = (_voiceRecordingElapsed.value / 1000),
                thumbnailPath = null
            )
        }
    }

    fun copyMessage(message: Message) {
        // Copy to clipboard - would be implemented with ClipboardManager
    }

    override fun onCleared() {
        super.onCleared()
        stopRecording(cancelled = true)
    }
}
