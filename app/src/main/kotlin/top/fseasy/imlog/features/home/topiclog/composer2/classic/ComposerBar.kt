package top.fseasy.imlog.features.home.topiclog.composer2.classic

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.fseasy.imlog.R
import top.fseasy.imlog.features.home.topiclog.composer.LocalComposerFocusRequester
import top.fseasy.imlog.features.home.topiclog.composer2.tapOrHoldToRecordGesture
import top.fseasy.imlog.ui.theme.ImlogTheme
import top.fseasy.imlog.ui.theme.composerStyle

private val ComposerBarMinHeight = 62.dp
private const val EXPAND_LINE_THRESHOLD = 4 // 出现「展开编辑」按钮时的行数阈值
private val ActionButtonSize = 30.dp // 按钮外径
private val ActionIconSize = 24.dp // 图标尺寸（保证充足内部留白）
private val ActionBorderWidth = 1.5.dp // 增强黑色外框粗细感

@Composable
internal fun ComposerBar(
    textFieldState: TextFieldState,
    onAttachmentClick: () -> Unit,
    onSendText: () -> Unit,
    onTextFieldFocusChanged: (focused: Boolean) -> Unit,
    onHoldToRecordAction: (ComposerHoldToRecordAction) -> Unit,
    modifier: Modifier = Modifier,
    isAttachmentOpen: Boolean = false,
) {
  var isVoiceMode by remember { mutableStateOf(false) }
  var lineCount by remember { mutableIntStateOf(1) }
  // 1. 核心性能优化：打字时仅当「有内容 / 无内容」状态翻转时才触发重组，避免每个按键重组整个 Bar
  val hasSendableContent by remember {
    derivedStateOf { textFieldState.text.isNotEmpty() }
  }

  val focusRequester = LocalComposerFocusRequester.current ?: remember { FocusRequester() }
  val keyboardController = LocalSoftwareKeyboardController.current

  Row(
      modifier =
          modifier
              .fillMaxWidth()
              .height(IntrinsicSize.Min)
              .heightIn(min = ComposerBarMinHeight, max = 120.dp)
              .padding(vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    // 中间动态切换区域 (键盘文本输入 vs 按住录音)
    Box(
        modifier = Modifier.weight(1f).padding(start = 12.dp, end = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
      if (isVoiceMode) {
        PushToRecordArea(onHoldToRecordAction = onHoldToRecordAction)
      } else {
        TextInputArea(
            textFieldState = textFieldState,
            focusRequester = focusRequester,
            onTextFieldFocusChanged = onTextFieldFocusChanged,
            onHoldToRecordAction = onHoldToRecordAction,
            onLineCountChanged = { count -> lineCount = count }
        )
      }
    }

    // 右侧操作区 (发送 / 切换模式 / 附件切换)
    ComposerActionArea(
        hasSendableContent = hasSendableContent,
        textFieldLineCount = lineCount,
        isVoiceMode = isVoiceMode,
        isAttachmentOpen = isAttachmentOpen,
        onSendText = onSendText,
        onAttachmentClick = onAttachmentClick,
        onToggleVoiceMode = {
          isVoiceMode = !isVoiceMode
          if (isVoiceMode) {
            keyboardController?.hide()
          } else {
            focusRequester.requestFocus()
            keyboardController?.show()
          }
        },
        modifier = Modifier.padding(end = 12.dp),
    )
  }
}

// -------------------------------------------------------------------------------------
// 抽离的独立子组件
// -------------------------------------------------------------------------------------

/** 语音对讲状态下的 Push To Record 按钮 */
@Composable
private fun PushToRecordArea(
    onHoldToRecordAction: (ComposerHoldToRecordAction) -> Unit,
    modifier: Modifier = Modifier,
) {
  Box(
      modifier =
          modifier
              .fillMaxWidth()
              .heightIn(min = 40.dp)
              .tapOrHoldToRecordGesture(
                  onTap = {},
                  onHoldStart = {
                    onHoldToRecordAction(ComposerHoldToRecordAction.StartHoldToTalk(it))
                  },
                  onMove = {
                    onHoldToRecordAction(ComposerHoldToRecordAction.MoveHoldToTalkTouch(it))
                  },
                  onRelease = {
                    onHoldToRecordAction(ComposerHoldToRecordAction.ReleaseHoldToTalk(it))
                  },
              ),
      contentAlignment = Alignment.Center,
  ) {
    Text(
        text = "Push to Record",
        style = MaterialTheme.typography.composerStyle.copy(fontWeight = FontWeight.Bold),
        color = Color.Black,
    )
  }
}

/** 文本输入区组件，将聚焦和打字手势隔离在此处 */
@Composable
private fun TextInputArea(
    textFieldState: TextFieldState,
    focusRequester: FocusRequester,
    onTextFieldFocusChanged: (Boolean) -> Unit,
    onHoldToRecordAction: (ComposerHoldToRecordAction) -> Unit,
    onLineCountChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
  var isFocused by remember { mutableStateOf(false) }
  val keyboardController = LocalSoftwareKeyboardController.current

  // 避免高频文本变化触发外部范围的不必要重组
  val isTextEmpty by remember { derivedStateOf { textFieldState.text.isEmpty() } }
  val showGestureOverlay by remember {
    derivedStateOf { !isFocused && textFieldState.text.isEmpty() }
  }

  Box(
      modifier = modifier.fillMaxWidth(),
      contentAlignment = Alignment.CenterStart,
  ) {
    BasicTextField(
        state = textFieldState,
        textStyle = MaterialTheme.typography.composerStyle,
        cursorBrush = SolidColor(Color.Black),
        modifier =
            Modifier.fillMaxWidth().focusRequester(focusRequester).onFocusChanged {
              isFocused = it.isFocused
              onTextFieldFocusChanged(it.isFocused)
            },
        onTextLayout = { getLayoutResult ->
          getLayoutResult()?.let { layoutResult ->
            onLineCountChanged(layoutResult.lineCount)
          }
        },
        decorator = { innerTextField ->
          Box(contentAlignment = Alignment.CenterStart) {
            if (isTextEmpty) {
              val hintResId =
                  if (!isFocused) R.string.composer_default_stage_input_placeholder
                  else R.string.composer_text_input_placeholder
              Text(
                  text = stringResource(hintResId),
                  color = Color.Gray,
                  style = MaterialTheme.typography.composerStyle,
              )
            }
            innerTextField()
          }
        },
    )

    // 仅在未聚焦且无内容时拦截手势
    if (showGestureOverlay) {
      Box(
          modifier =
              Modifier.matchParentSize()
                  .tapOrHoldToRecordGesture(
                      onTap = {
                        focusRequester.requestFocus()
                        keyboardController?.show()
                      },
                      onHoldStart = {
                        onHoldToRecordAction(ComposerHoldToRecordAction.StartHoldToTalk(it))
                      },
                      onMove = {
                        onHoldToRecordAction(ComposerHoldToRecordAction.MoveHoldToTalkTouch(it))
                      },
                      onRelease = {
                        onHoldToRecordAction(ComposerHoldToRecordAction.ReleaseHoldToTalk(it))
                      },
                  )
      )
    }
  }
}

/** 右侧按钮行为组合 */
@Composable
private fun ComposerActionArea(
    hasSendableContent: Boolean,
    textFieldLineCount: Int,
    isVoiceMode: Boolean,
    isAttachmentOpen: Boolean,
    onSendText: () -> Unit,
    onAttachmentClick: () -> Unit,
    onToggleVoiceMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
  // 附件旋转动画
  val attachmentRotateAngle by
      animateFloatAsState(
          targetValue = if (isAttachmentOpen) 45f else 0f,
          label = "attachmentRotateAngle",
      )

  val density = LocalDensity.current
  var currentHeightPx by remember { mutableIntStateOf(0) }

  // 基准最小高度换算为像素（+2px 容差，防止浮点计算精度抖动）
  val composerBarInitialHeightPx =
      remember(density) {
        with(density) { ComposerBarMinHeight.roundToPx() }
      }

  // 1. 核心判断：容器到底有没有真正“被撑高”！
  // 只有当高度真正突破了 minHeight，才算膨胀，才需要沉底
  val isContainerExpanded by remember {
    derivedStateOf { currentHeightPx > (composerBarInitialHeightPx + 2) }
  }

  val shouldShowExpandButton = textFieldLineCount >= EXPAND_LINE_THRESHOLD

  Row(
      modifier = modifier.fillMaxHeight().onSizeChanged { currentHeightPx = it.height },
      verticalAlignment = Alignment.CenterVertically,
  ) {
    AnimatedContent(
        targetState = hasSendableContent,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "composerActionButtons",
    ) { canSend ->
      if (canSend) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement =
                when {
                  // 1. 出现两个按钮：顶底拉开
                  shouldShowExpandButton -> Arrangement.SpaceBetween
                  // 2. 多行撑高：沉底，跟随最后一行文字
                  isContainerExpanded -> Arrangement.Bottom
                  // 3. 单行：绝对垂直居中
                  else -> Arrangement.Center
                },
        ) {
          // 顶部全屏按钮（按需渲染）
          if (shouldShowExpandButton) {
            CircularIconButton(onClick = {}, borderColor = null) {
              Icon(
                  imageVector = Icons.Rounded.Face,
                  contentDescription = "Expand to Fullscreen",
                  tint = Color.Gray,
                  modifier = Modifier.size(ActionIconSize),
              )
            }
          }

          CircularIconButton(
              onClick = onSendText,
              backgroundColor = MaterialTheme.colorScheme.primary,
          ) {
            Icon(
                painter = painterResource(R.drawable.icon_arrow_upward_alt_24px),
                contentDescription = "Send",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(ActionIconSize),
            )
          }
        }
      } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          // 切换语音 / 键盘
          CircularIconButton(
              onClick = onToggleVoiceMode,
              borderColor = Color.Black,
          ) {
            val iconRes =
                if (isVoiceMode) R.drawable.icon_keyboard_keys_24px
                else R.drawable.icon_hold_to_record_24px_scaled
            Icon(
                painter = painterResource(iconRes),
                contentDescription = "Toggle Input Mode",
                tint = Color.Black,
                modifier = Modifier.size(ActionIconSize),
            )
          }

          // 附件展开/收起按钮
          CircularIconButton(
              onClick = onAttachmentClick,
              borderColor = Color.Black,
          ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "Toggle Attachment",
                tint = Color.Black,
                // 2. 核心性能优化：在 graphicsLayer 中旋转，走 Draw 阶段，不触发组件 Recompose
                modifier =
                    Modifier.size(ActionIconSize).graphicsLayer {
                      rotationZ = attachmentRotateAngle
                    },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun CircularIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = ActionButtonSize, // 视觉尺寸
    backgroundColor: Color = Color.Transparent,
    borderColor: Color? = null,
    content: @Composable () -> Unit,
) {
  // 外层透明 Box 负责拦截触摸事件与热区

  Box(
      modifier =
          modifier
              .height(48.dp) // Try to follow the Material design specification.
              .width(38.dp) // but have to reduce the width
              .clickable(
                  interactionSource = remember { MutableInteractionSource() },
                  indication = null, // 点击水波纹可根据需要决定在内圈还是外圈
                  onClick = onClick,
              ),
      contentAlignment = Alignment.Center,
  ) {
    // 内层 Box 负责视觉渲染（圆形背景/边框）
    Box(
        modifier =
            Modifier.size(size)
                .clip(CircleShape)
                .then(
                    if (borderColor != null)
                        Modifier.border(ActionBorderWidth, borderColor, CircleShape)
                    else Modifier
                )
                .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
      content()
    }
  }
}

@Preview("Circular Icon Preview")
@Composable
fun CircularIconPreview() {
  ImlogTheme {
    Surface {
      Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        CircularIconButton(
            onClick = {},
            borderColor = Color.Black,
        ) {
          val iconRes = R.drawable.icon_keyboard_keys_24px

          Icon(
              painter = painterResource(iconRes),
              contentDescription = "Toggle Input Mode",
              tint = Color.Black,
              modifier = Modifier.size(ActionIconSize),
          )
        }

        CircularIconButton(
            onClick = {},
            borderColor = Color.Black,
        ) {
          val iconRes = R.drawable.icon_hold_to_record_24px_scaled
          Icon(
              painter = painterResource(iconRes),
              contentDescription = "Toggle Input Mode",
              tint = Color.Black,
              modifier = Modifier.size(ActionIconSize),
          )
        }

        // 附件展开/收起按钮
        CircularIconButton(
            onClick = {},
            borderColor = Color.Black,
        ) {
          Icon(
              imageVector = Icons.Rounded.Add,
              contentDescription = "Toggle Attachment",
              tint = Color.Black,
              modifier = Modifier.size(ActionIconSize),
          )
        }
      }
    }
  }
}
