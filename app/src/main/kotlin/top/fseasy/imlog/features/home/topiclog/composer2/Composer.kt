package top.fseasy.imlog.features.home.topiclog.composer2

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import top.fseasy.imlog.features.home.topiclog.composer.MessageComposerViewModel

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MessageComposer2(
    onNavigateBack: () -> Unit,
    onShowSnackbar: (messageResId: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MessageComposerViewModel = hiltViewModel(),
    initialVoiceSubMode: VoiceSubMode = VoiceSubMode.TapToRecord,
    onBaseHeightPxChanged: (Int) -> Unit = {},
) {
  var inputMode by remember { mutableStateOf(MessageInputMode.Default) }
  var voiceSubMode by remember { mutableStateOf(initialVoiceSubMode) }
  var inputText by remember { mutableStateOf("") }

  // 附件模式特定的状态（移动 vs 复制）
  var isMoveAttachmentEnabled by remember { mutableStateOf(false) }

  // 录音状态
  var tapRecordingState by remember { mutableStateOf<TapRecordingState>(TapRecordingState.Idle) }
  var isHoldingVoice by remember { mutableStateOf(false) }
  var holdGestureTarget by remember { mutableStateOf(HoldToTalkGestureTarget.Normal) }



  val micPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
  val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

  // 返回键逻辑：先关面板/退出录音，再返回上一页
  val isRecording = tapRecordingState !is TapRecordingState.Idle
  BackHandler(enabled = inputMode != MessageInputMode.Default || isRecording) {
    when {
      inputMode == MessageInputMode.Camera -> inputMode = MessageInputMode.Default
      isRecording -> tapRecordingState = TapRecordingState.Idle
      inputMode != MessageInputMode.Default -> inputMode = MessageInputMode.Default
      else -> onNavigateBack()
    }
  }
}

@Composable
fun Composer2Content(
    currentInputMode: MessageInputMode,
    inputText: String,

    modifier: Modifier = Modifier,
) {
  val focusManager = LocalFocusManager.current
  val focusRequester = remember { FocusRequester() }
  val haptic = LocalHapticFeedback.current

  Box(modifier = modifier.fillMaxWidth()) {
    // 主卡片组件
    MessageComposerCard(
        inputMode = currentInputMode,
        inputText = inputText,
        tapRecordingState = tapRecordingState,
        focusRequester = focusRequester,
        onInputTextChange = { inputText = it },
        // Tab 模式切换
        onSelectTab = { targetMode ->
          when (targetMode) {
            MessageInputMode.Camera -> {
              if (cameraPermission.status.isGranted) {
                focusManager.clearFocus()
                inputMode = MessageInputMode.Camera
              } else {
                cameraPermission.launchPermissionRequest()
              }
            }
            MessageInputMode.Recorder -> {
              if (micPermission.status.isGranted) {
                focusManager.clearFocus()
                inputMode = MessageInputMode.Recorder
              } else {
                micPermission.launchPermissionRequest()
              }
            }
            MessageInputMode.Default -> {
              inputMode = MessageInputMode.Default
              focusRequester.requestFocus()
            }
            MessageInputMode.Attachment -> {
              focusManager.clearFocus()
              inputMode = MessageInputMode.Attachment
            }
          }
        },
        onToggleMoveAttachment = { isMoveAttachmentEnabled = !isMoveAttachmentEnabled },
        // 卡片空白处的长按对讲 (零心理负担速记)
        onHoldVoiceStart = {
          if (micPermission.status.isGranted) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            isHoldingVoice = true
          } else {
            micPermission.launchPermissionRequest()
          }
        },
        onHoldVoiceEnd = { shouldSend ->
          isHoldingVoice = false
          if (shouldSend) {
            // 发送长按音频
          }
        },
        onHoldGestureChange = { target ->
          if (target == HoldToTalkGestureTarget.Lock && holdGestureTarget != HoldToTalkGestureTarget.Lock) {
            // P9 -> P7 升格：划到 Lock 后，直接解放双手切入免提录音台
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            isHoldingVoice = false
            inputMode = MessageInputMode.Recorder
            voiceSubMode = VoiceSubMode.TapToRecord
            tapRecordingState = TapRecordingState.Recording(durationSec = 10)
          }
          holdGestureTarget = target
        },
        // 免提录音台操作
        onTapRecordStart = { tapRecordingState = TapRecordingState.Recording(0) },
        onTapRecordPause = { dur -> tapRecordingState = TapRecordingState.Paused(dur, false) },
        onTapRecordResume = { dur -> tapRecordingState = TapRecordingState.Recording(dur) },
        onTapRecordDiscard = { tapRecordingState = TapRecordingState.Idle },
        onTogglePreviewPlay = {
          (tapRecordingState as? TapRecordingState.Paused)?.let {
            tapRecordingState = it.copy(isPlayingPreview = !it.isPlayingPreview)
          }
        },
        // 发送
        onSendText = {
          if (inputText.isNotBlank()) {
            // sendTextMessage(inputText)
            inputText = ""
          }
        },
        onSendVoice = {
          // sendAudioMessage()
          tapRecordingState = TapRecordingState.Idle
          inputMode = MessageInputMode.Default
        },
        // 附件即选即发
        onSelectAttachment = { type ->
          // sendAttachmentDirectly(type, isMove = isMoveAttachmentEnabled)
          inputMode = MessageInputMode.Default // 发送完毕后自然切回默认打字
        },
    )

    // P9: 弧形对讲波纹手势浮层
    if (isHoldingVoice) {
      HoldToSpeakOverlay(
          gestureTarget = holdGestureTarget,
          durationText = "0:10",
          modifier = Modifier.matchParentSize(),
      )
    }

    // P6: 相机全屏取景图层
    if (inputMode == MessageInputMode.Camera) {
      CameraOverlay(
          onClose = { inputMode = MessageInputMode.Default },
          modifier = Modifier.fillMaxSize(),
      )
    }
  }
}
