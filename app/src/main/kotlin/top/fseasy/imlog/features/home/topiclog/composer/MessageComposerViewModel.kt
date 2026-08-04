package top.fseasy.imlog.features.home.topiclog.composer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import timber.log.Timber
import top.fseasy.imlog.data.mapper.toUriStr
import top.fseasy.imlog.data.util.MimeTypeUtils
import top.fseasy.imlog.data.util.VoiceRecorderState
import top.fseasy.imlog.di.ApplicationIoScope
import top.fseasy.imlog.domain.model.AuthState
import top.fseasy.imlog.domain.model.MessageDraft
import top.fseasy.imlog.domain.model.MessageFactory
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.MessageRepository
import top.fseasy.imlog.domain.repository.TopicRepository
import top.fseasy.imlog.domain.repository.UserRepository
import top.fseasy.imlog.domain.usecase.StoragePathUseCase
import top.fseasy.imlog.domain.usecase.sendattachment.ResolveMetadataResult
import top.fseasy.imlog.domain.usecase.sendattachment.SendUriUseCaseBase
import top.fseasy.imlog.domain.usecase.sendattachment.SendUriUseCaseFactory
import top.fseasy.imlog.domain.usecase.sendattachment.SendVoiceMessageUseCase
import top.fseasy.imlog.domain.usecase.sendattachment.fileMimeTypeToMessageType
import top.fseasy.imlog.navigation.MainScreen
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

private const val MESSAGE_INPUT_MODE_UI_STATE = "message_input_mode"
private const val MESSAGE_INPUT_TEXT_UI_STATE = "message_input_text"

// SQLite writing is single thread, so it is useless that set it higher
private const val ATTACHMENT_RESOLVE_METADATA_CONCURRENCY = 6
private const val ATTACHMENT_COPY_CONCURRENCY = 3

@OptIn(FlowPreview::class)
@HiltViewModel
class MessageComposerViewModel
@Inject
constructor(
    storagePathUseCase: StoragePathUseCase,
    sendVoiceMessageUseCase: SendVoiceMessageUseCase,
    userRepository: UserRepository,
    private val savedStateHandle: SavedStateHandle,
    private val messageRepository: MessageRepository,
    private val topicRepository: TopicRepository,
    private val sendUriUseCaseFactory: SendUriUseCaseFactory,
    @param:ApplicationContext private val context: Context,
    @ApplicationIoScope private val applicationIoScope: CoroutineScope,
) : ViewModel() {
  private val topicId = savedStateHandle.toRoute<MainScreen.TopicTimeline>().topicId
  private val authState = userRepository.authState

  val voiceRecorderStateHolder =
      VoiceRecorderStateHolder(
              coroutineScope = viewModelScope,
              storagePathUseCase = storagePathUseCase,
              sendVoiceMessageUseCase = sendVoiceMessageUseCase,
              context = context,
          )
          .also(::addCloseable)

  // Bind to SavedStateHandle to remember it while app in background/killed-restore state
  val inputTextUiState: StateFlow<String> =
      savedStateHandle.getStateFlow(MESSAGE_INPUT_TEXT_UI_STATE, initialValue = "")

  val inputModeUiState: StateFlow<MessageInputModeParcelable?> =
      savedStateHandle.getStateFlow(
          MESSAGE_INPUT_MODE_UI_STATE,
          initialValue = null,
      )

  private val _uiEffect = Channel<ComposerUiEffect>()
  val uiEffect = _uiEffect.receiveAsFlow()

  init {
    if (!savedStateHandle.contains(MESSAGE_INPUT_MODE_UI_STATE)) {
      // Init in cold start, let's load from db.
      launchWithTopicUserId { topicId, userId ->
        val draft =
            topicRepository.getMessageDraft(userId = userId, topicId = topicId) ?: MessageDraft()
        savedStateHandle[MESSAGE_INPUT_TEXT_UI_STATE] = draft.text
        savedStateHandle[MESSAGE_INPUT_MODE_UI_STATE] = draft.inputMode?.toParcelable()
      }
    }
    inputTextUiState
        .debounce(500.milliseconds)
        .distinctUntilChanged()
        .onEach { saveDraftToDb() }
        .launchIn(viewModelScope)
    inputModeUiState.onEach { saveDraftToDb() }.launchIn(viewModelScope)
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

  fun updateInputMode(newInputMode: MessageInputModeParcelable?) {
    savedStateHandle[MESSAGE_INPUT_MODE_UI_STATE] = newInputMode
  }

  /** After press Back button, change draftMetaUiState and send _uiEffect to trigger page action. */
  fun handleBackPress() {
    viewModelScope.launch {
      val inputMode = inputModeUiState.value

      when (inputMode) {
        null -> _uiEffect.send(ComposerUiEffect.PopBackStack)

        // just clear inputMode, the UI will then update
        MessageInputModeParcelable.Attachment,
        MessageInputModeParcelable.Text,
        -> clearInputMode() // reset to null mode

        // TODO: should trigger pause and keep the state so that the state can be saved to draft db.
        // HERE we just stop the recorder and sending message, and pop back.
        MessageInputModeParcelable.Voice -> {
          val recorderState = voiceRecorderStateHolder.voiceRecordingUiState.value
          if (recorderState.recorderState == VoiceRecorderState.Recording) {
            // TODO: support recording pause logic! issue #6
            stopVoiceRecordingAndSendVoiceMessage()
            _uiEffect.send(ComposerUiEffect.Vibrate)
          }
          // always pop back
          _uiEffect.send(ComposerUiEffect.PopBackStack)
          // Here we don't need to change inputMode.
        }
      }
    }
  }

  fun startVoiceRecording() {
    launchWithTopicUserId { _, userId ->
      voiceRecorderStateHolder.startVoiceRecording(userId)
    }
  }

  fun cancelVoiceRecording() {
    viewModelScope.launch {
      voiceRecorderStateHolder.cancelVoiceRecording()
    }
  }

  fun stopVoiceRecordingAndSendVoiceMessage() {
    launchWithTopicUserId(useLocalScope = false) { topicId, userId ->
      voiceRecorderStateHolder.stopVoiceRecordingAndSendVoiceMessage(
          topicId = topicId,
          userId = userId,
      )
    }
  }

  /**
   * @param unifiedMessageType if it has the same messageType, set it to avoid get-metadata logic,
   *   else leave it null
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  fun sendMultipleAttachments(uris: List<Uri>, unifiedMessageType: MessageType? = null) {
    /**
     * @return the usecase instance is used for further processing. because the usecase is decided
     *   by the inputMessageType, while it's not available in the ResolveMetadataResult, so it's
     *   necessary to return it
     */
    suspend fun resolveMetadata(
        uri: Uri,
        userId: UserId,
        topicId: TopicId,
        instant: Instant,
    ): Pair<SendUriUseCaseBase, ResolveMetadataResult?> {
      val inputMessageType =
          unifiedMessageType
              ?: fileMimeTypeToMessageType(
                  MimeTypeUtils.getMimeType(
                      context,
                      uri = uri,
                  )
              )
      val usecase = sendUriUseCaseFactory.get(inputMessageType)
      val result =
          usecase.resolveMetadata(
              srcUriStr = uri.toUriStr(),
              userId = userId,
              topicId = topicId,
              messageTimestampMs = instant.toEpochMilliseconds(),
          )
      return usecase to result
    }

    launchWithTopicUserId(useLocalScope = false) { topicId, userId ->
      val now = Clock.System.now()
      // make a dummy ASC timestamps for each uri, step = 10ms
      val ascTimestamps = List(uris.size) { now + (it * 10).milliseconds }
      // 1. resolve metadata parallel
      // - because it's pair, so it's very hard to elimit the null from ResolveMetadataResult?
      val usecaseToMetadataResults =
          uris
              .zip(ascTimestamps)
              .asFlow()
              .flatMapMerge(ATTACHMENT_RESOLVE_METADATA_CONCURRENCY) { (uri, instant) ->
                flow {
                  val pairResult =
                      resolveMetadata(
                          uri = uri,
                          userId = userId,
                          topicId = topicId,
                          instant = instant,
                      )
                  emit(pairResult)
                }
              }
              .toList()
              .sortedBy { it.second?.messageTimestampMs ?: 0 }
      // 2. insert to db in sequence
      val insertDbResult = usecaseToMetadataResults.mapNotNull { (usecase, metadataResult) ->
        metadataResult?.let { nonnullMetadata ->
          usecase.insertInitialMessage(nonnullMetadata)?.let { messageId ->
            Triple(usecase, nonnullMetadata, messageId)
          }
        }
      }
      // 3. copy & start background
      val successCount =
          insertDbResult
              .asFlow()
              .flatMapMerge(ATTACHMENT_COPY_CONCURRENCY) { (usecase, metadataResult, messageId) ->
                flow {
                  val isSuccess =
                      usecase.copyToInternalAndStartBackgroundTask(metadataResult, messageId)
                  emit(isSuccess)
                }
              }
              .toList()
              .count { it }
      if (successCount < uris.size) {
        Timber.w(
            "sendMultipleAttachments failed on some cases: input=%d, resolveMetadata=%d, insertDb=%d, final=%s",
            uris.size,
            usecaseToMetadataResults.count { it.second != null },
            insertDbResult.size,
            successCount,
        )
      } else {
        Timber.d("sendMultipleAttachments success on all %d cases", successCount)
      }
    }
  }

  fun sendTextMessage(content: String) {
    launchWithTopicUserId(useLocalScope = false) { topicId, userId ->
      val now = System.currentTimeMillis()
      val textMsg = MessageFactory.createText(topicId, userId, text = content, timestampMs = now)
      messageRepository.saveTextMessage(textMsg)
    }
  }

  private fun saveDraftToDb() {
    val inputMode = inputModeUiState.value?.toDomain()
    val inputText = inputTextUiState.value
    launchWithTopicUserId { topicId, userId ->
      val draft =
          MessageDraft(
            inputMode = inputMode,
            quotedMessage = null, // TODO: add quoteMessage impl
            text = inputText,
          )
      try {
        topicRepository.setMessageDraft(
            userId = userId,
            topicId = topicId,
            draft = draft,
        )
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        Timber.d(e, "Failed to set message draft")
      }
    }
  }

  private fun launchWithTopicUserId(
      useLocalScope: Boolean = true,
      block: suspend (topicId: TopicId, userId: UserId) -> Unit,
  ) {
    val userId = (authState.value as? AuthState.Authenticated)?.userId ?: return

    val scope = if (useLocalScope) viewModelScope else applicationIoScope

    scope.launch {
      block(topicId, userId)
    }
  }
}
