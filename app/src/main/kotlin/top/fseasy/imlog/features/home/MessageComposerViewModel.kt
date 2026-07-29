package top.fseasy.imlog.features.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import top.fseasy.imlog.data.mapper.toFileWithCreatingDirectories
import top.fseasy.imlog.data.mapper.toUriStr
import top.fseasy.imlog.domain.model.MessageDraft
import top.fseasy.imlog.domain.model.MessageFactory
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.MessageRepository
import top.fseasy.imlog.domain.repository.TopicRepository
import top.fseasy.imlog.domain.repository.UserRepository
import top.fseasy.imlog.domain.usecase.StoragePathUseCase
import top.fseasy.imlog.data.util.VoiceRecorder
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.VoiceRecordingState
import top.fseasy.imlog.features.home.model.ComposerDraftMeta
import top.fseasy.imlog.features.home.model.ComposerUiEffect
import top.fseasy.imlog.features.home.model.MessageInputModeParcelable
import top.fseasy.imlog.features.home.model.SendFileMessageUseCases
import top.fseasy.imlog.features.home.model.toDomain
import top.fseasy.imlog.features.home.model.toParcelable

import top.fseasy.imlog.navigation.MainScreen
import java.io.File
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds


private const val MESSAGE_INPUT_MODE_UI_STATE = "message_input_mode"
private const val MESSAGE_INPUT_TEXT_UI_STATE = "message_input_text"


@OptIn(FlowPreview::class)
@HiltViewModel
class MessageComposerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val topicRepository: TopicRepository,
    private val storagePathUseCase: StoragePathUseCase,
    private val sendFileMessageUseCase: SendFileMessageUseCases,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    private val topicId = savedStateHandle.toRoute<MainScreen.TopicTimeline>().topicId
    private val userId = userRepository.observeCurrentUserIdOrNull()
        .stateIn(
            viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = null
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

    // Bind to SavedStateHandle to remember it while app in background/killed-restore state
    val inputTextUiState: StateFlow<String> =
        savedStateHandle.getStateFlow(MESSAGE_INPUT_TEXT_UI_STATE, initialValue = "")

    val inputModeUiState: StateFlow<MessageInputModeParcelable?> = savedStateHandle.getStateFlow(
        MESSAGE_INPUT_MODE_UI_STATE, initialValue = null
    )

    private val _uiEffect = Channel<ComposerUiEffect>()

    init {
        if (!savedStateHandle.contains(MESSAGE_INPUT_MODE_UI_STATE)) {
            // Init in cold start, let's load from db.
            launchWithTopicUserId { topicId, userId ->
                val draft = topicRepository.getMessageDraft(userId = userId, topicId = topicId)
                    ?: MessageDraft()
                savedStateHandle[MESSAGE_INPUT_TEXT_UI_STATE] = draft.text
                savedStateHandle[MESSAGE_INPUT_MODE_UI_STATE] = draft.inputMode?.toParcelable()
            }
        }
        inputTextUiState.debounce(500.milliseconds)
            .distinctUntilChanged()
            .onEach { saveDraftToDb() }
            .launchIn(viewModelScope)
        inputModeUiState.onEach { saveDraftToDb() }
            .launchIn(viewModelScope)
    }

    fun updateInputText(text: String) {
        savedStateHandle[MESSAGE_INPUT_TEXT_UI_STATE] = text
    }

    fun setInputModeToText() {
        savedStateHandle[MESSAGE_INPUT_MODE_UI_STATE] = MessageInputModeParcelable.Text
    }

    fun setInputModeToVoice() {
        savedStateHandle[MESSAGE_INPUT_MODE_UI_STATE] = MessageInputModeParcelable.Voice
    }

    fun clearInputMode() {
        savedStateHandle[MESSAGE_INPUT_MODE_UI_STATE] = null
    }

    /**
     * After press Back button, change draftMetaUiState and send _uiEffect to trigger page action.
     */
    fun handleBackPress(isKeyboardVisible: Boolean) {
        viewModelScope.launch {
            val inputMode = inputModeUiState.value

            when (inputMode) {
                null -> _uiEffect.send(ComposerUiEffect.PopBackStack)

                MessageInputModeParcelable.Attachment -> {
                    clearInputMode() // reset to null mode
                }

                MessageInputModeParcelable.Voice -> {
                    val recorderState = voiceRecordingUiState.value
                    if (recorderState.voiceRecordingState == VoiceRecordingState.Recording) {
                        // TODO: support recording pause logic! issue #6
                        stopVoiceRecordingAndSendVoiceMessage()
                        _uiEffect.send(ComposerUiEffect.Vibrate)
                    }
                    // always pop back
                    _uiEffect.send(ComposerUiEffect.PopBackStack)
                    // Here we don't need to change inputMode.
                }

                MessageInputModeParcelable.Text -> {
                    when (isKeyboardVisible) {
                        // keyboard shown
                        true -> {
                            _uiEffect.send(ComposerUiEffect.HideKeyboard)

                            if (isInputTextEmpty()) {
                                // reset to null
                                clearInputMode()
                            }
                            // if not empty, keep it to text mode.
                        }
                        // keyboard not shown. pop back
                        else -> {
                            _uiEffect.send(ComposerUiEffect.PopBackStack)
                        }
                    }
                }
            }
        }
    }


    fun startVoiceRecording() {
        launchWithTopicUserId { _, userId ->
            val outputFile = generateVoiceRecordingOutputFileInMessageCacheRule(userId = userId)
            voiceRecorder.start(context, outputFile)
        }
    }

    fun cancelVoiceRecording() {
        viewModelScope.launch {
            voiceRecorder.cancel()
        }
    }

    fun stopVoiceRecordingAndSendVoiceMessage() {
        launchWithTopicUserId(useLocalScope = false) { topicId, userId ->
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

    fun sendMultipleAttachments(uris: List<Uri>, uniformMessageType: MessageType?) {
        // TODO: split the db operation out, then first insert db concurrently, finally do the rests.
    }

    fun sendTextMessage(content: String) {
        launchWithTopicUserId(useLocalScope = false) { topicId, userId ->
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

    private fun isInputTextEmpty() = inputTextUiState.value == ""

    private fun saveDraftToDb() {
        val inputMode = inputModeUiState.value?.toDomain()
        val inputText = inputTextUiState.value
        launchWithTopicUserId { topicId, userId ->
            val draft = MessageDraft(
                inputMode = inputMode, quoteMessage = null, // TODO: add quoteMessage impl
                text = inputText
            )
            try {
                topicRepository.setMessageDraft(
                    userId = userId, topicId = topicId, draft = draft
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.d(e, "Failed to set message draft")
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

    private fun launchWithTopicUserId(
        useLocalScope: Boolean = true,
        block: suspend (topicId: TopicId, userId: UserId) -> Unit,
    ) {
        val userId = userId.value ?: return

        val scope =
            if (useLocalScope) viewModelScope else ProcessLifecycleOwner.get().lifecycleScope

        scope.launch(Dispatchers.IO) {
            block(topicId, userId)
        }
    }
}

