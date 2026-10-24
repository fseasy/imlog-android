package top.fseasy.imlog.features.home.topiclog.composer2.tabbedcard

import android.Manifest
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import top.fseasy.imlog.features.home.topiclog.composer.MessageInputModeUi
import top.fseasy.imlog.features.home.topiclog.composer.VoiceRecordingUiState
import top.fseasy.imlog.features.home.topiclog.composer2.Composer2ViewModel
import top.fseasy.imlog.features.home.topiclog.composer2.ComposerAction
import top.fseasy.imlog.features.home.topiclog.composer2.ComposerAttachmentStageAction
import top.fseasy.imlog.features.home.topiclog.composer2.ComposerBottomContextAction
import top.fseasy.imlog.features.home.topiclog.composer2.ComposerDefaultStageAction
import top.fseasy.imlog.features.home.topiclog.composer2.ComposerInputModeContextActionUiState
import top.fseasy.imlog.features.home.topiclog.composer2.ComposerStage
import top.fseasy.imlog.features.home.topiclog.composer2.ComposerVoiceRecordingAction
import top.fseasy.imlog.features.home.topiclog.composer2.GestureEvaluation
import top.fseasy.imlog.features.home.topiclog.composer2.HoldToSpeakOverlay
import top.fseasy.imlog.features.home.topiclog.composer2.HoldToTalkGestureTarget
import top.fseasy.imlog.features.home.topiclog.composer2.RadialGeometry

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MessageTabbedCardComposer(
  onNavigateBack: () -> Unit,
  onShowSnackbar: (messageResId: Int) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: Composer2ViewModel = hiltViewModel(),
  onBaseHeightPxChanged: (Int) -> Unit = {},
) {
  var inputMode by remember { mutableStateOf<MessageInputModeUi?>(null) }
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
      stage = composerStage,
      textFieldState = textFieldState,
      redDotStages = emptySet(),
      sendEnabled = true,
      composerContextActionState =
        ComposerInputModeContextActionUiState(compressAlbumEnabled = true),
      voiceRecodingUiStateHolder = voiceRecordingUiStateHolder,
      onSelectComposerStage = viewModel::updateComposerStage,
      onStartVoiceRecording = viewModel::startVoiceRecording,
      onSendVoiceRecording = viewModel::stopVoiceRecordingAndSendVoiceMessage,
      onCancelVoiceRecoding = viewModel::cancelVoiceRecording,
      onSendText = viewModel::sendTextMessage,
      onShowSnackbar = onShowSnackbar,
      onBaseHeightPxChanged = onBaseHeightPxChanged,
  )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun ComposerContent(
  stage: ComposerStage,
  redDotStages: Set<ComposerStage>,
  sendEnabled: Boolean,
  textFieldState: TextFieldState,
  composerContextActionState: ComposerInputModeContextActionUiState,
  voiceRecodingUiStateHolder: State<VoiceRecordingUiState>,
  onSelectComposerStage: (ComposerStage) -> Unit,
  onStartVoiceRecording: () -> Unit,
  onSendVoiceRecording: () -> Unit,
  onCancelVoiceRecoding: () -> Unit,
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

  val onAction: (ComposerAction) -> Unit = { action ->
    when (action) {
      is ComposerAction.SelectComposerStage -> {
        onSelectComposerStage(action.stage)
      }
      is ComposerAction.SelectInputMode -> {
        TODO()
      }
      is ComposerAttachmentStageAction.SelectAlbums -> {
        TODO()
      }
      is ComposerAttachmentStageAction.SelectAudios -> {
        TODO()
      }
      is ComposerAttachmentStageAction.SelectFiles -> {
        TODO()
      }
      ComposerBottomContextAction.ToggleCompressAlbum -> {
        TODO()
      }
      is ComposerDefaultStageAction.ChangeFocus -> {}
      is ComposerDefaultStageAction.StartHoldToTalk -> {
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

      is ComposerDefaultStageAction.MoveHoldToTalkTouch -> {
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
      is ComposerDefaultStageAction.ReleaseHoldToTalk -> {
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
      ComposerDefaultStageAction.SendText -> {
        onSendText()
      }
      ComposerVoiceRecordingAction.DiscardVoiceRecording -> {
        onCancelVoiceRecoding()
      }
      ComposerVoiceRecordingAction.PauseVoiceRecording -> {
        TODO()
      }
      ComposerVoiceRecordingAction.ResumeVoiceRecording -> {
        TODO()
      }
      ComposerVoiceRecordingAction.SendVoiceRecording -> {
        onSendVoiceRecording()
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
      }
      ComposerVoiceRecordingAction.StartVoiceRecording -> {
        onStartVoiceRecording()
      }
      ComposerVoiceRecordingAction.ToggleVoiceRecordingPreviewPlay -> {
        TODO()
      }
    }
  }

  // This is a Full Screen box to support Edge2Edge overlay
  Box(modifier = modifier.fillMaxSize()) {
    // 主卡片组件
    MessageComposerCard(
        stage = stage,
        redDotStages = redDotStages,
        textFieldState = textFieldState,
        sendEnabled = sendEnabled,
        composerContextActionState = composerContextActionState,
        onAction = onAction,
        modifier =
          Modifier.align(Alignment.BottomCenter)
              .fillMaxWidth()
              .safeDrawingPadding()
              .imePadding()
              .onSizeChanged {
                onBaseHeightPxChanged(it.height)
              },
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
