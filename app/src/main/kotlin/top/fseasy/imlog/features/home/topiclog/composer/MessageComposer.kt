package top.fseasy.imlog.features.home.topiclog.composer

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.yield
import timber.log.Timber
import top.fseasy.imlog.domain.model.MessageType

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageComposer(
    onNavigateBack: () -> Unit,
    onSendMessageCallback: () -> Unit,
    viewModel: MessageComposerViewModel = hiltViewModel(),
) {
  val inputMode by viewModel.inputModeUiState.collectAsStateWithLifecycle()
  val inputText by viewModel.inputTextUiState.collectAsStateWithLifecycle()
  // NOTE: here we just get the State instead of reading the value, so that the high-frequent
  //       UI refresh can be limited the lowest UI component
  val voiceRecordingUiStateHolder =
      viewModel.voiceRecorderStateHolder.voiceRecordingUiState.collectAsStateWithLifecycle()

  // Response for back-handler
  BackHandler(true) { viewModel.handleBackPress() }

  val hapticFeedback = LocalHapticFeedback.current
  LaunchedEffect(Unit) {
    viewModel.uiEffect.collect { effect ->
      when (effect) {
        is ComposerUiEffect.PopBackStack -> onNavigateBack()
        is ComposerUiEffect.Vibrate ->
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
      }
    }
  }

  MessageComposerContent(
      inputText = inputText,
      inputMode = inputMode,
      voiceRecodingUiStateHolder = voiceRecordingUiStateHolder,
      inputModeSetActions =
          InputModeSetActions(
              onTextInputFocusChange = { isFocused ->
                // Avoid duplicated update the inputMode to Text as it has side effects
                if (isFocused && inputMode != MessageInputModeUiState.Text) {
                  viewModel.updateInputMode(MessageInputModeUiState.Text)
                }
                // Don't process unFocused here, as we don't know the reason caused unfocus
                // The other parts will take responsibility for input mode changing.
                // For example:
                // is it attachment button click? -> set to Attachment in button action
                // is it outer area click? -> The parent component will handle this condition!
              },
              onVoiceInputSingleClick = {
                viewModel.updateInputMode(MessageInputModeUiState.Voice)
              },
              onAttachmentClick = {
                if (inputMode != MessageInputModeUiState.Attachment) {
                  viewModel.updateInputMode(MessageInputModeUiState.Attachment)
                } else {
                  // NOTE: here we just clear the inputMode, it's simple and acceptable
                  // WeChat logic: set it to Text Mode.
                  //        If we need to follow, we have to change the `focus` flow
                  //        as TextField don't have logic to change focus from outside.
                  viewModel.updateInputMode(null)
                }
              },
              onTextModeImeHide = viewModel::clearInputMode,
          ),
      onInputTextChange = { text -> viewModel.updateInputText(text) },
      onSendText = {
        viewModel.sendTextMessage(inputText)
        onSendMessageCallback()
      },
      onSendVoice = {
        viewModel.stopVoiceRecordingAndSendVoiceMessage()
        onSendMessageCallback()
      },
      onCancelVoiceRecoding = { viewModel.cancelVoiceRecording() },
      onSelectAlbums = { uris ->
        viewModel.sendMultipleAttachments(uris)
        onSendMessageCallback()
      },
      onSelectAudios = { uris ->
        viewModel.sendMultipleAttachments(
            uris,
            MessageType.Audio,
        )
        onSendMessageCallback()
      },
      onSelectFiles = { uris ->
        viewModel.sendMultipleAttachments(
            uris,
            MessageType.GenericFile,
        )
        onSendMessageCallback()
      },
      onCloseExpanded = { viewModel.clearInputMode() },
  )
}

val LocalComposerFocusRequester = staticCompositionLocalOf<FocusRequester?> { null }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageComposerContent(
    inputText: String,
    inputMode: MessageInputModeUiState?,
    voiceRecodingUiStateHolder: State<VoiceRecordingUiState>,
    inputModeSetActions: InputModeSetActions,
    onInputTextChange: (String) -> Unit,
    onSendText: () -> Unit,
    onSendVoice: () -> Unit,
    onCancelVoiceRecoding: () -> Unit,
    onSelectAlbums: (uris: List<Uri>) -> Unit,
    onSelectAudios: (uris: List<Uri>) -> Unit,
    onSelectFiles: (uris: List<Uri>) -> Unit,
    onCloseExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {

  // IME will first get the `back` button and then close the IME.
  // We need to listen this behavious and clear the inputMode directly to avoid the bug:
  // - Need to press 3 times back-button to navigate-back
  val isImeVisible = WindowInsets.isImeVisible

  LaunchedEffect(isImeVisible) {
    if (!isImeVisible && inputMode == MessageInputModeUiState.Text) {
      inputModeSetActions.onTextModeImeHide()
    }
  }

  // set/clear focus
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val focusRequester = remember { FocusRequester() }
  LaunchedEffect(inputMode) {
    Timber.i("recompose on inputMode = $inputMode")
    if (inputMode != MessageInputModeUiState.Text) {
      // NOTE the order!
      // MUST do Hide before clear focus! Show/Hide needs a focus element!
      keyboardController?.hide()
      focusManager.clearFocus()
    } else {
      yield()
      focusRequester.requestFocus()
      keyboardController?.show()
    }
  }

  Surface(tonalElevation = 2.dp, contentColor = MaterialTheme.colorScheme.secondary) {
    Column(modifier = modifier) {
      // TODO: add the QuoteMessage Panel
      CompositionLocalProvider(LocalComposerFocusRequester provides focusRequester) {
        UserInputRow(
            inputText = inputText,
            inputMode = inputMode,
            voiceRecordingUiStateHolder = voiceRecodingUiStateHolder,
            inputModeSetActions = inputModeSetActions,
            onInputTextChange = onInputTextChange,
            onSendText = onSendText,
            onSendVoice = onSendVoice,
            onCancelVoiceRecoding = onCancelVoiceRecoding,
            modifier = modifier,
        )
      }
      AttachmentExpanded(
          inputMode = inputMode,
          onSelectAlbums = onSelectAlbums,
          onSelectAudios = onSelectAudios,
          onSelectFiles = onSelectFiles,
          closeExpanded = onCloseExpanded,
          modifier = modifier,
          height = 72.dp, // TODO: set it to IME height to avoid menu row jitter
      )
    }
  }
}
