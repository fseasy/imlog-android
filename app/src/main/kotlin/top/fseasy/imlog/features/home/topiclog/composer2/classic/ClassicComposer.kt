package top.fseasy.imlog.features.home.topiclog.composer2.classic

import android.Manifest
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import top.fseasy.imlog.R
import top.fseasy.imlog.domain.model.ScreenOrientation
import top.fseasy.imlog.features.home.topiclog.composer.MessageInputModeUi
import top.fseasy.imlog.features.home.topiclog.composer.VoiceRecordingUiState
import top.fseasy.imlog.features.home.topiclog.composer2.Composer2ViewModel
import top.fseasy.imlog.features.home.topiclog.composer2.ComposerStage
import top.fseasy.imlog.features.home.topiclog.composer2.GestureEvaluation
import top.fseasy.imlog.features.home.topiclog.composer2.HoldToSpeakOverlay
import top.fseasy.imlog.features.home.topiclog.composer2.HoldToTalkGestureTarget
import top.fseasy.imlog.features.home.topiclog.composer2.RadialGeometry

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ClassicComposer(
    onNavigateBack: () -> Unit,
    onShowSnackbar: (messageResId: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: Composer2ViewModel = hiltViewModel(),
    onBaseHeightPxChanged: (Int) -> Unit = {},
) {

  val dockState by viewModel.classicComposerUiState.dockUiState.collectAsStateWithLifecycle()
  val textFieldState = viewModel.textFieldState
  val composerStage by viewModel.composerStage.collectAsStateWithLifecycle()

  val voiceRecordingUiStateHolder =
      viewModel.voiceRecorderStateHolder.voiceRecordingUiState.collectAsStateWithLifecycle()
  val onVoiceButtonSingleClick = {
    viewModel.updateInputMode(MessageInputModeUi.HoldToRecord)
    // start recording NOW
    viewModel.startVoiceRecording()
  }

  val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

  ComposerContent(
      dockState = dockState,
      textFieldState = textFieldState,
      voiceRecodingUiStateHolder = voiceRecordingUiStateHolder,
      onSelectComposerStage = viewModel::updateComposerStage,
      onStartVoiceRecording = viewModel::startVoiceRecording,
      onSendVoiceRecording = viewModel::stopVoiceRecordingAndSendVoiceMessage,
      onCancelVoiceRecoding = viewModel::cancelVoiceRecording,
      onSendText = viewModel::sendTextMessage,
      onShowSnackbar = onShowSnackbar,
      onBaseHeightPxChanged = onBaseHeightPxChanged,
      onUpdateCachedImeHeightInDp = viewModel.classicComposerUiState::updateCachedImeHeight,
      onToggleAttachment = viewModel.classicComposerUiState::toggleAttachmentPanel,
      onTextFieldFocusChanged = viewModel.classicComposerUiState::onTextFieldFocusChanged,
  )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun ComposerContent(
    dockState: ComposerDockState,
    textFieldState: TextFieldState,
    voiceRecodingUiStateHolder: State<VoiceRecordingUiState>,
    onSelectComposerStage: (ComposerStage) -> Unit,
    onStartVoiceRecording: () -> Unit,
    onSendVoiceRecording: () -> Unit,
    onCancelVoiceRecoding: () -> Unit,
    onUpdateCachedImeHeightInDp: (ScreenOrientation, heightValueInDp: Float) -> Unit,
    onToggleAttachment: () -> Unit,
    onTextFieldFocusChanged: (focused: Boolean) -> Unit,
    onSendText: () -> Unit,
    onShowSnackbar: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onBaseHeightPxChanged: (Int) -> Unit = {},
) {
  val focusManager = LocalFocusManager.current
  val focusRequester = remember { FocusRequester() }
  val haptic = LocalHapticFeedback.current
  val density = LocalDensity.current

  var activeGeometry by remember { mutableStateOf<RadialGeometry?>(null) }
  var holdGestureEvaluation by remember { mutableStateOf(GestureEvaluation.Idle) }

  val micPermissionState =
      rememberPermissionState(Manifest.permission.RECORD_AUDIO) { isGranted ->
        if (isGranted) {
          onStartVoiceRecording()
        } else {
          onShowSnackbar(R.string.composer_recording_fail_on_permission)
        }
      }

  val onHoldToRecordAction: (ComposerHoldToRecordAction) -> Unit = { action ->
    when (action) {
      is ComposerHoldToRecordAction.StartHoldToTalk -> {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val geometry =
            RadialGeometry.create(
                originPosition = action.rootOffset,
                density = density,
            )
        activeGeometry = geometry
        holdGestureEvaluation = geometry.evaluate(action.rootOffset)
        if (micPermissionState.status.isGranted) {
          onStartVoiceRecording()
        } else {
          micPermissionState.launchPermissionRequest()
        }
      }

      is ComposerHoldToRecordAction.MoveHoldToTalkTouch -> {
        val currentEvaluation =
            activeGeometry?.evaluate(action.rootOffset) ?: GestureEvaluation.Idle
        val target = currentEvaluation.confirmedTarget
        if (
            target != holdGestureEvaluation.confirmedTarget &&
                target != HoldToTalkGestureTarget.Normal
        ) {
          haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        }
        holdGestureEvaluation = currentEvaluation
      }
      is ComposerHoldToRecordAction.ReleaseHoldToTalk -> {
        when (holdGestureEvaluation.confirmedTarget) {
          HoldToTalkGestureTarget.Normal -> {
            onSendVoiceRecording()
          }
          HoldToTalkGestureTarget.Cancel -> {
            onCancelVoiceRecoding()
          }
          HoldToTalkGestureTarget.Lock -> {
            onSelectComposerStage(ComposerStage.Recording)
          }
        }
        haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)

        activeGeometry = null
        holdGestureEvaluation = GestureEvaluation.Idle
      }
    }
  }

  // This is a Full Screen box to support Edge2Edge overlay
  Box(modifier = modifier.fillMaxSize()) {
    // 主卡片组件
    ClassicComposerDock(
        state = dockState,
        textFieldState = textFieldState,
        onHoldToRecordAction = onHoldToRecordAction,
        onUpdateCachedImeHeightInDp = onUpdateCachedImeHeightInDp,
        onToggleAttachment = onToggleAttachment,
        onTextFieldFocusChanged = onTextFieldFocusChanged,
        modifier =
            Modifier.align(Alignment.BottomCenter).onSizeChanged {
              onBaseHeightPxChanged(it.height)
            },
        onSendText = onSendText,
    )

    activeGeometry?.run {
      HoldToSpeakOverlay(
          geometry = this,
          evaluationProvider = { holdGestureEvaluation },
          voiceRecodingUiStateHolder = voiceRecodingUiStateHolder,
          modifier = Modifier.matchParentSize(),
      )
    }

    // P6: 相机全屏取景图层
    //    if (inputMode == MessageInputMode.Camera) {
    //      CameraOverlay(
    //          onClose = { inputMode = MessageInputMode.Default },
    //          modifier = Modifier.fillMaxSize(),
    //      )
    //    }
  }
}
