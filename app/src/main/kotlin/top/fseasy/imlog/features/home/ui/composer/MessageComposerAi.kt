package top.fseasy.imlog.features.home.ui.composer

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// 输入框模式：普通模式 vs 独立录音栏模式
enum class ComposerMode {
    NORMAL, RECORDING
}

/**
 * 根组件：IM 输入栏
 */
@Composable
fun UserInput(
    onMessageSent: (String) -> Unit,
    onVoiceSent: () -> Unit,
    onFileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var composerMode by rememberSaveable { mutableStateOf(ComposerMode.NORMAL) }
    var textState by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    var isFocused by remember { mutableStateOf(false) }

    // 判断是否处于“打字态”：聚焦 或 有文本输入
    val isTypingState = isFocused || textState.text.isNotEmpty()

    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        AnimatedContent(
            targetState = composerMode,
            transitionSpec = {
                fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
            },
            label = "ComposerModeTransition"
        ) { mode ->
            when (mode) {
                ComposerMode.NORMAL -> {
                    NormalComposerRow(
                        textState = textState,
                        isTypingState = isTypingState,
                        onTextChanged = { textState = it },
                        onFocusChanged = { isFocused = it },
                        onSendClick = {
                            if (textState.text.isNotBlank()) {
                                onMessageSent(textState.text)
                                textState = TextFieldValue()
                            }
                        },
                        onFileClick = onFileClick,
                        onVoiceSingleClick = {
                            composerMode = ComposerMode.RECORDING
                        },
                        onVoiceLongPress = {
                            onVoiceSent()
                        }
                    )
                }

                ComposerMode.RECORDING -> {
                    VoiceRecordingBar(
                        onCancel = { composerMode = ComposerMode.NORMAL },
                        onSend = {
                            onVoiceSent()
                            composerMode = ComposerMode.NORMAL
                        }
                    )
                }
            }
        }
    }
}

/**
 * 普通输入模式 Row (左侧:输入框+语音胶囊 | 右侧:文件/发送 1:1 槽位)
 */
@Composable
private fun NormalComposerRow(
    textState: TextFieldValue,
    isTypingState: Boolean,
    onTextChanged: (TextFieldValue) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onSendClick: () -> Unit,
    onFileClick: () -> Unit,
    onVoiceSingleClick: () -> Unit,
    onVoiceLongPress: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom // 底部对齐，支持多行文本向上生长
    ) {
        // === 1. 左侧可变区域：文本框 + 语音大胶囊 ===
        ComposerInputAndVoiceRow(
            textState = textState,
            isTypingState = isTypingState,
            onTextChanged = onTextChanged,
            onFocusChanged = onFocusChanged,
            onVoiceSingleClick = onVoiceSingleClick,
            onVoiceLongPress = onVoiceLongPress,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // === 2. 右侧固定槽位：文件与发送按钮 原地 1:1 切换 ===
        RightActionSlot(
            isTypingState = isTypingState,
            onSendClick = onSendClick,
            onFileClick = onFileClick
        )
    }
}

/**
 * 左侧区域：文本框（自动延伸） + 语音胶囊（收缩/淡出）
 */
@Composable
private fun ComposerInputAndVoiceRow(
    textState: TextFieldValue,
    isTypingState: Boolean,
    onTextChanged: (TextFieldValue) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onVoiceSingleClick: () -> Unit,
    onVoiceLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1.1 文本输入框 (占据剩余空间的 1f，随着语音按钮隐藏自动平滑向右延伸)
        ComposerTextField(
            textState = textState,
            onTextChanged = onTextChanged,
            onFocusChanged = onFocusChanged,
            modifier = Modifier.weight(1f)
        )

        // 1.2 语音胶囊按钮（输入态时平滑向右收缩并隐藏）
        AnimatedVisibility(
            visible = !isTypingState,
            enter = expandHorizontally(expandFrom = Alignment.End) + fadeIn(),
            exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut()
        ) {
            Row {
                Spacer(modifier = Modifier.width(8.dp))
                VoiceCapsuleButton(
                    onClick = onVoiceSingleClick,
                    onLongPress = onVoiceLongPress
                )
            }
        }
    }
}

/**
 * 文本输入框 (适度的圆角 12.dp)
 */
@Composable
private fun ComposerTextField(
    textState: TextFieldValue,
    onTextChanged: (TextFieldValue) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }

    // 稍微收紧圆角到 12.dp，比全圆角更加自然协调
    val shape = RoundedCornerShape(12.dp)
    val backgroundColor = if (isFocused) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val borderColor = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent

    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (textState.text.isEmpty()) {
            Text(
                text = "输入内容...",
                style = TextStyle(fontSize = 15.sp, color = Color.Gray)
            )
        }
        BasicTextField(
            value = textState,
            onValueChange = onTextChanged,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 22.dp, max = 100.dp)
                .onFocusChanged {
                    isFocused = it.isFocused
                    onFocusChanged(it.isFocused)
                },
            textStyle = TextStyle(
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
        )
    }
}

/**
 * 语音大胶囊按钮
 */
@Composable
private fun VoiceCapsuleButton(
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(12.dp), // 保持和输入框一致的圆角
        modifier = modifier
            .height(38.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongPress() }
                )
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Mic,
                contentDescription = "语音",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "语音",
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 14.sp,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

/**
 * 右侧固定 40.dp 槽位，实现【文件按钮】与【发送按钮】1:1 原地缩放渐变切换
 */
@Composable
private fun RightActionSlot(
    isTypingState: Boolean,
    onSendClick: () -> Unit,
    onFileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(38.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = isTypingState,
            transitionSpec = {
                (scaleIn(initialScale = 0.6f) + fadeIn()) togetherWith
                    (scaleOut(targetScale = 0.6f) + fadeOut())
            },
            label = "RightSlotTransition"
        ) { isTyping ->
            if (isTyping) {
                // 发送按钮
                IconButton(
                    onClick = onSendClick,
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "发送",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                // 文件/加号按钮
                IconButton(
                    onClick = onFileClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "更多/文件",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

/**
 * 模式 2：单点独立录制栏 (取代常规 Composer Row)
 */
@Composable
private fun VoiceRecordingBar(
    onCancel: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 简易呼吸灯动画
    var blink by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            blink = !blink
            delay(600)
        }
    }
    val alphaAnim by animateFloatAsState(targetValue = if (blink) 1f else 0.2f, label = "blink")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .height(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFFFF0F0)), // 浅红背景
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 取消按钮
        IconButton(onClick = onCancel) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "删除录音",
                tint = Color(0xFFFF3B30)
            )
        }

        // 录音计时与闪烁红点
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .alpha(alphaAnim)
                    .background(Color(0xFFFF3B30), CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "00:03",
                color = Color(0xFFFF3B30),
                fontSize = 14.sp,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // 操作组（暂停 / 发送）
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { /* 暂停/播放逻辑 */ }) {
                Icon(
                    imageVector = Icons.Outlined.Pause,
                    contentDescription = "暂停",
                    tint = Color(0xFFFF3B30),
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = onSend,
                modifier = Modifier
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "发送语音",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun UserInputPreview() {
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF4F5F7)),
            contentAlignment = Alignment.BottomCenter
        ) {
            UserInput(
                onMessageSent = {},
                onVoiceSent = {},
                onFileClick = {}
            )
        }
    }
}