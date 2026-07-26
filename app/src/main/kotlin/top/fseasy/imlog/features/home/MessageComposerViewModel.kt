package top.fseasy.imlog.features.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.fseasy.imlog.data.mapper.toFileWithCreatingDirectories
import top.fseasy.imlog.data.mapper.toUriStr
import top.fseasy.imlog.domain.model.MessageFactory
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.MessageRepository
import top.fseasy.imlog.domain.repository.UserRepository
import top.fseasy.imlog.domain.usecase.StoragePathUseCase
import top.fseasy.imlog.domain.usecase.sendfilemessage.SendAudioMessageUseCase
import top.fseasy.imlog.domain.usecase.sendfilemessage.SendGenericFileMessageUseCase
import top.fseasy.imlog.domain.usecase.sendfilemessage.SendImageMessageUseCase
import top.fseasy.imlog.domain.usecase.sendfilemessage.SendVideoMessageUseCase
import top.fseasy.imlog.domain.usecase.sendfilemessage.SendVoiceMessageUseCase
import top.fseasy.imlog.features.home.domain.VoiceRecorder
import top.fseasy.imlog.navigation.MainScreen
import java.io.File
import javax.inject.Inject

class SendFileMessageUseCases @Inject constructor(
    val sendAudio: SendAudioMessageUseCase,
    val sendVoice: SendVoiceMessageUseCase,
    val sendVideo: SendVideoMessageUseCase,
    val sendImage: SendImageMessageUseCase,
    val sendGenericFile: SendGenericFileMessageUseCase,
)


private const val MESSAGE_INPUT_TEXT = "message_input_text"

@HiltViewModel
class MessageComposerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val storagePathUseCase: StoragePathUseCase,
    private val sendFileMessageUseCase: SendFileMessageUseCases,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    private val topicId = savedStateHandle.toRoute<MainScreen.TopicTimeline>().topicId
    private val userId = userRepository.observeCurrentUserIdOrNull()
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val voiceRecorder = VoiceRecorder(viewModelScope).also(::addCloseable)

    val voiceRecordingUiState: StateFlow<VoiceRecordingUiState> = combine(
        voiceRecorder.state, voiceRecorder.elapsedMs
    ) { state, elapsedMs ->
        VoiceRecordingUiState(state, elapsedMs)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VoiceRecordingUiState()
    )
    val inputText = savedStateHandle.getStateFlow(MESSAGE_INPUT_TEXT, "")

    init {
        launchWithTopicUserId { topicId, userId ->
            messageRepository.getDraft()
        }
    }

    fun startVoiceRecording() {
        launchWithTopicUserId { _, userId ->
            val outputFile =
                generateVoiceRecordingOutputFileInMessageCacheRule(userId = userId)
            voiceRecorder.start(context, outputFile)
        }
    }

    fun cancelVoiceRecording() {
        viewModelScope.launch {
            voiceRecorder.cancel()
        }
    }

    fun stopVoiceRecordingAndSendVoiceMessage() {
        launchWithTopicUserId { topicId, userId ->
            voiceRecorder.stop()
                ?.let {
                    // It follows the message cache file generating rule, so only filename is necessary
                    sendFileMessageUseCase.sendVoice(
                        it.name,
                        userId = userId,
                        topicId = topicId,
                        messageTimestampMs = System.currentTimeMillis(),
                    )
                }
        }
    }

    fun sendTextMessage(content: String) {
        launchWithTopicUserId { topicId, userId ->
            val now = System.currentTimeMillis()
            val textMsg =
                MessageFactory.createText(topicId, userId, text = content, timestampMs = now)
            messageRepository.saveTextMessage(textMsg)
        }
    }

    fun sendImageMessage(uri: Uri) {
        launchWithTopicUserId { topicId, userId ->
            sendFileMessageUseCase.sendImage(
                srcUriStr = uri.toUriStr(),
                userId = userId,
                topicId = topicId,
                messageTimestampMs = System.currentTimeMillis()
            )
        }
    }

    fun sendVideoMessage(uri: Uri) {
        launchWithTopicUserId { topicId, userId ->
            sendFileMessageUseCase.sendVideo(
                srcUriStr = uri.toUriStr(),
                userId = userId,
                topicId = topicId,
                messageTimestampMs = System.currentTimeMillis()
            )
        }
    }

    fun sendAudioMessage(uri: Uri) {
        launchWithTopicUserId { tid, uid ->
            sendFileMessageUseCase.sendAudio(
                uri.toUriStr(),
                userId = uid,
                topicId = tid,
                messageTimestampMs = System.currentTimeMillis(),
            )
        }
    }

    fun updateTextDraftState(text: String) {
        inputText.update {}
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

    private inline fun launchWithTopicUserId(crossinline block: suspend (topicId: TopicId, userId: UserId) -> Unit) {
        viewModelScope.launch {
            when (val u = userId.value) {
                is UserId -> block(topicId, u)
                else -> Unit
            }
        }
    }
}

