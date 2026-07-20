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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.fseasy.imlog.data.mapper.toFileWithCreatingDirectories
import top.fseasy.imlog.data.mapper.toUriStr
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
import top.fseasy.imlog.domain.usecase.SendMessageFileUseCase
import top.fseasy.imlog.domain.usecase.StoragePathUseCase
import top.fseasy.imlog.features.home.domain.VoiceRecorder
import java.io.File
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
    private val topicRepository: TopicRepository,
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    private val storagePathUseCase: StoragePathUseCase,
    private val sendFileMessageUseCase: SendMessageFileUseCase,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    private val _topicId = MutableStateFlow<TopicId?>(null)

    val voiceRecorder = VoiceRecorder(viewModelScope)

    @OptIn(ExperimentalCoroutinesApi::class)
    val contentUiState: StateFlow<ContentUiState> = combine(
        _topicId.filterNotNull(),
        userRepository.observeCurrentUserIdOrNull()
            .filterNotNull()
    ) { tid, uid ->
        tid to uid
    }.distinctUntilChanged()
        .flatMapLatest { (tid, uid) ->
            combine(
                topicRepository.observeTopic(tid),
                messageRepository.observeTopicMessages(tid, uid),
            ) { topic, messages ->
                when (topic) {
                    null -> ContentUiState.Error("Failed to load Topic for id: $tid")
                    else -> ContentUiState.Success(
                        topic = topic,
                        messages = messages.map { m ->
                            MessageUiState(
                                message = m, thumbnailModel = buildThumbnailModel(m, uid)
                            )
                        },
                        currentUserId = uid,
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ContentUiState.Loading
        )

    val voiceRecordingUiState: StateFlow<VoiceRecordingUiState> = combine(
        voiceRecorder.state, voiceRecorder.elapsedMs
    ) { state, elapsedMs ->
        VoiceRecordingUiState(state, elapsedMs)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VoiceRecordingUiState()
    )

    override fun onCleared() {
        super.onCleared()
        voiceRecorder.cancel()
    }

    fun loadTopic(topicId: TopicId) {
        _topicId.value = topicId
    }

    fun onVoiceRecorderStateChange(state: VoiceRecordingState) {
        when (state) {
            VoiceRecordingState.Idle -> voiceRecorder.cancel()
            VoiceRecordingState.Recording -> {
                launchWithTopicUserId { topicId, userId ->
                    val outputFile =
                        generateVoiceRecordingOutputFileInMessageCacheRule(userId = userId)
                    voiceRecorder.start(context, outputFile)
                }
            }

            VoiceRecordingState.Stopped -> {
                voiceRecorder.stop()
                    ?.let {
                        // It follows the message cache file generating rule, so only filename is necessary
                        sendVoiceMessage(it.name)
                    }
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
        launchWithTopicUserId({ tid, uid ->
            sendFileMessageUseCase.sendAudio(
                uri.toUriStr(),
                userId = uid,
                topicId = tid,
                messageTimestampMs = System.currentTimeMillis(),
            )
        })
    }

    fun sendVoiceMessage(audioCacheFilename: String) {
        launchWithTopicUserId({ tid, uid ->
            sendFileMessageUseCase.sendVoice(
                audioCacheFilename,
                userId = uid,
                topicId = tid,
                messageTimestampMs = System.currentTimeMillis(),
            )
        })
    }

    fun copyMessage(message: Message) {
        // TODO: 实现剪贴板复制
    }


    private fun buildThumbnailModel(
        message: Message,
        userId: UserId,
    ): ResourceModel? {
        if (message.type != MessageType.IMAGE && message.type != MessageType.VIDEO) {
            return null
        }
        return null
    }

    private inline fun launchWithTopicUserId(crossinline block: suspend (topicId: TopicId, userId: UserId) -> Unit) {
        viewModelScope.launch {
            when (val s = contentUiState.value) {
                is ContentUiState.Success -> block(s.topic.id, s.currentUserId)
                else -> Unit
            }
        }
    }

    private suspend fun generateVoiceRecordingOutputFileInMessageCacheRule(
        userId: UserId,
        now: Long = System.currentTimeMillis(),
    ): File {
        val filename = storagePathUseCase.buildTimestampedFilename(
            now, originalFilename = VoiceRecorder.generateOutputAudioDefaultFilename("voice")
        )
        val outputFilePath = storagePathUseCase.buildMessageCacheFileStoragePath(
            userId = userId, filename = filename
        )
        val outputFile = outputFilePath.toFileWithCreatingDirectories(context)
        return outputFile
    }
}