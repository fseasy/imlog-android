package top.fseasy.imlog.features.home.topiclog.composer2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.fseasy.imlog.domain.model.MessageInputMode
import top.fseasy.imlog.features.home.topiclog.composer.MessageInputModeUi

@Composable
fun MessageComposerCard(
    inputMode: MessageInputModeUi?,
    inputText: String,
    onAction: (ComposerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
  val stage = inputMode.decideComposerStage()
  // 1. 最外层遮罩底板：紧贴屏幕边缘，屏蔽背景 Timeline 内容渗透，并拦截触摸防止点击穿透
  Surface(
      modifier = modifier.fillMaxWidth().imePadding(),
      color = MaterialTheme.colorScheme.surface, // 与页面主底色保持一致，形成天然遮罩
  ) {
    // 2. 内部悬浮输入卡片：保留优雅的圆角和外边距
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer, // M3 推荐的容器浅灰/暗灰底色
    ) {
      Column(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
          verticalArrangement = Arrangement.SpaceBetween,
      ) {
        // 上层舞台区 (Stage Area)
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp)) {
          when (stage) {
            ComposerStage.Default -> {
              TextStage(
                  text = inputText,
                  focusRequester = focusRequester,
                  onTextChange = onInputTextChange,
                  onHoldVoiceStart = onHoldVoiceStart,
              )
            }
            ComposerStage.Recording -> {
              MicStage(
                  subMode = voiceSubMode,
                  recordingState = tapRecordingState,
                  onStartRecord = onTapRecordStart,
                  onPause = { onTapRecordPause(20) },
                  onResume = { onTapRecordResume(20) },
                  onDiscard = onTapRecordDiscard,
                  onTogglePlay = onTogglePreviewPlay,
                  onHoldStart = onHoldVoiceStart,
                  onHoldEnd = onHoldVoiceEnd,
                  onGestureChange = onHoldGestureChange,
              )
            }
            ComposerStage.Attachment -> {
              ComposerAttachmentStage(onSelect = onSelectAttachment)
            }
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 下层三段式底栏
        Composer2BottomBar(
            uiState =
                Composer2BottomBarUiState(
                    currentStage = stage,
                    sendEnabled = true, // TODO
                    contextActionState =
                        ComposerInputModeContextActionUiState(
                            compressAlbumEnabled = true // TODO
                        ),
                ),
            onAction = { action ->
              when (action) {
                is ComposerAction.SelectInputMode -> onSelectTab(action.mode)
                ComposerAction.SendMessage -> {
                  when (inputMode) {
                    MessageInputMode.Default -> onSendText()
                    else -> {}
                  }
                }
                ComposerBottomContextAction.ToggleCompressAlbum -> TODO()
              }
            },
        )
      }
    }
  }
}
