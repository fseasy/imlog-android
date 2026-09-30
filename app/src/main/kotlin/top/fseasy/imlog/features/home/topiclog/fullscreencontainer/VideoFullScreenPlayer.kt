package top.fseasy.imlog.features.home.topiclog.fullscreencontainer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.ui.compose.PlayerSurface
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.fseasy.imlog.R
import top.fseasy.imlog.data.util.MediaPlaybackState
import top.fseasy.imlog.data.util.PlayerStatus
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.util.safeDivision
import top.fseasy.imlog.domain.util.toAppMessageTimeFormat
import top.fseasy.imlog.features.home.topiclog.timeline.MessageContentUiModel
import top.fseasy.imlog.features.home.topiclog.timeline.aspectRatio
import top.fseasy.imlog.features.home.topiclog.timeline.messagebubble.WaveformSlider
import top.fseasy.imlog.features.home.topiclog.toMediaInputId
import top.fseasy.imlog.features.home.topiclog.toOverlayItemKey
import top.fseasy.imlog.ui.components.gesture.DismissSource
import top.fseasy.imlog.ui.components.gesture.DismissibleBox
import top.fseasy.imlog.ui.components.gesture.ZoomableBox
import top.fseasy.imlog.ui.components.gesture.rememberDismissState
import top.fseasy.imlog.ui.components.gesture.rememberTransformHandoverChain
import top.fseasy.imlog.ui.components.gesture.rememberZoomableState
import top.fseasy.imlog.ui.components.overlaylayout.OverlayLayoutScope
import top.fseasy.imlog.ui.components.overlaylayout.PreviewOverlayLayoutScope
import top.fseasy.imlog.ui.theme.ImlogTheme
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Composable
fun OverlayLayoutScope.VideoFullScreenPlayer(
    messageId: MessageId,
    content: MessageContentUiModel.Video,
    player: Player,
    playbackState: MediaPlaybackState,
    activePlayPositionHolder: State<Duration>,
    inactivePlayPosition: Duration,
    onTogglePlay: () -> Unit,
    onSeek: (Float) -> Unit,
    onSpeedCycle: () -> Unit,
    modifier: Modifier = Modifier,
) {

  VideoFullScreenPlayerContent(
      content = content,
      messageId = messageId,
      playbackState = playbackState,
      activePlayPositionHolder = activePlayPositionHolder,
      inactivePlayPosition = inactivePlayPosition,
      onTogglePlay = onTogglePlay,
      onSeek = onSeek,
      onSpeedCycle = onSpeedCycle,
      videoSurface = {
        PlayerSurface(
            player = player,
            modifier = Modifier.fillMaxSize().aspectRatio(content.aspectRatio),
        )
      },
      modifier = modifier,
  )
}

@Composable
fun OverlayLayoutScope.VideoFullScreenPlayerContent(
    messageId: MessageId,
    content: MessageContentUiModel.Video,
    playbackState: MediaPlaybackState,
    activePlayPositionHolder: State<Duration>,
    inactivePlayPosition: Duration,
    onTogglePlay: () -> Unit,
    onSeek: (Float) -> Unit,
    onSpeedCycle: () -> Unit,
    videoSurface: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
  var areControlsVisible by remember { mutableStateOf(true) }
  val toggleControlsVisible = { areControlsVisible = !areControlsVisible }

  val overlayItemKey = toOverlayItemKey(messageId)
  val zoomState = rememberZoomableState(overlayItemKey)
  val dismissState = rememberDismissState(overlayItemKey)

  val dismissFromVisualRect =
      remember(overlayItemKey) {
        { this.dismiss(overlayItemKey) }
      }

  val coroutineScope = rememberCoroutineScope()
  var zoomJob by remember { mutableStateOf<Job?>(null) }

  this.configureBackgroundTap(
      onTap = toggleControlsVisible,
      onDoubleTap = { offset ->
        // only reset room. this offset maybe weird as it's out of the image, so don't zoom in
        if (zoomState.isZoomed) {
          zoomJob?.cancel()
          zoomJob = coroutineScope.launch { zoomState.toggleZoom(offset) }
        }
      },
  )

  val handoverChain = rememberTransformHandoverChain(zoomState, dismissState)

  DismissibleBox(
      state = dismissState,
      enabled = { !zoomState.isZoomed },
      drawBackground = false,
      onDismissRequest = dismissFromVisualRect,
      animateSwipeDismiss = false,
      modifier =
          modifier
              .fillMaxSize()
              .overlayInteractiveTarget(
                  itemKey = overlayItemKey,
                  backgroundAlphaProvider = { dismissState.backgroundAlpha },
                  dismissTransformHandoverProvider = { handoverChain },
              ),
  ) {
    ZoomableBox(
        state = zoomState,
        coroutineScope = coroutineScope,
        onSingleTap = toggleControlsVisible,
        modifier = Modifier.fillMaxSize(),
    ) {
      videoSurface()
    }

    // Hidden controls during dismissing. state is discrete, so recomposition is acceptable
    val isOverlayTransitioning = this.transitionPhase.isTransitioning
    val isInteractingDismiss = dismissState.activeSource != DismissSource.None
    val shouldShowControls = areControlsVisible && !isOverlayTransitioning && !isInteractingDismiss
    AnimatedVisibility(
        visible = shouldShowControls,
        enter = fadeIn(animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(100)),
        modifier = Modifier.fillMaxSize(),
    ) {
      ControlsOverlay(
          messageId = messageId,
          content = content,
          playbackState = playbackState,
          activePlayPositionHolder = activePlayPositionHolder,
          inactivePlayPosition = inactivePlayPosition,
          onTogglePlay = onTogglePlay,
          onSeek = onSeek,
          onSpeedCycle = onSpeedCycle,
          onExit = dismissFromVisualRect,
      )
    }
  }
}

/** For single hand operation convenience */
private const val EXTRA_START_PADDING_IN_DP = 12

@Composable
private fun ControlsOverlay(
    messageId: MessageId,
    content: MessageContentUiModel.Video,
    playbackState: MediaPlaybackState,
    activePlayPositionHolder: State<Duration>,
    inactivePlayPosition: Duration,
    onTogglePlay: () -> Unit,
    onSeek: (Float) -> Unit,
    onSpeedCycle: () -> Unit,
    onExit: () -> Unit,
) {
  Box(modifier = Modifier.fillMaxSize()) {
    // close button
    IconButton(
        onClick = onExit,
        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Transparent),
        modifier =
            Modifier.align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp)
                .padding(start = EXTRA_START_PADDING_IN_DP.dp)
                .size(48.dp), // 保持 48dp 的点击手感
    ) {
      Box(
          modifier =
              Modifier.size(32.dp) // 视觉小黑圈
                  .background(
                      color = Color.Black.copy(alpha = 0.5f),
                      shape = CircleShape,
                  ),
          contentAlignment = Alignment.Center,
      ) {
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = stringResource(R.string.term_close),
            tint = Color.White,
            modifier = Modifier.size(24.dp),
        )
      }
    }

    val isVideoActive = playbackState.isThisMediaActive(toMediaInputId(messageId))
    PlayControllerDock(
        modifier =
            Modifier.fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 24.dp),
        isPlaying = playbackState.isThisMediaPlaying(toMediaInputId(messageId)),
        duration = playbackState.duration,
        speed = playbackState.speed,
        amplitudes = content.amplitudes,
        onTogglePlay = onTogglePlay,
        onSeek = onSeek,
        onSpeedCycle = onSpeedCycle,
        positionProvider = {
          if (isVideoActive) {
            activePlayPositionHolder.value
          } else {
            inactivePlayPosition
          }
        },
    )
  }
}

@Composable
private fun PlayControllerDock(
    isPlaying: Boolean,
    duration: Duration,
    speed: Float,
    amplitudes: List<Float>,
    onTogglePlay: () -> Unit,
    onSeek: (Float) -> Unit,
    onSpeedCycle: () -> Unit,
    positionProvider: () -> Duration,
    modifier: Modifier = Modifier,
) {
  val tintColor = Color.White

  // 记录用户是否点击过倍速（初始未点击显示“倍速”，点击后显示数值）
  var hasInteractedSpeed by rememberSaveable { mutableStateOf(false) }

  // 统一行高与间距参数，用于右侧实现严格的上下镜像对称
  val timeLineHeight = 14.dp
  val verticalSpacing = 4.dp

  Row(
      modifier = modifier.height(IntrinsicSize.Min), // 左右两列等高
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    val playPauseDescription =
        stringResource(if (isPlaying) R.string.term_media_pause else R.string.term_media_play)

    // 左侧：足够大的播放大按钮，填满高度且为正方形
    // 因右列上下对称，大按钮垂直居中时，其尖尖自然对准波形中轴，上沿与 time/duration 平齐
    IconButton(
        onClick = onTogglePlay,
        modifier =
            Modifier.fillMaxHeight().aspectRatio(1f).padding(start = EXTRA_START_PADDING_IN_DP.dp),
    ) {
      Icon(
          imageVector =
              if (isPlaying) {
                ImageVector.vectorResource(R.drawable.icon_pause_rounded_fill)
              } else {
                Icons.Rounded.PlayArrow
              },
          contentDescription = playPauseDescription,
          tint = tintColor,
          modifier = Modifier.fillMaxSize(), // 图标视觉比例适中居中
      )
    }

    // 右侧列：三段式布局（顶部时间 - 中间波形 - 底部镜像占位）
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
    ) {
      // 1. 顶部行：time / duration（靠左对齐）
      Row(
          modifier = Modifier.fillMaxWidth().height(timeLineHeight),
          horizontalArrangement = Arrangement.Start,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
            text = positionProvider().toAppMessageTimeFormat(),
            style = MaterialTheme.typography.labelSmall,
            color = tintColor.copy(alpha = 0.7f),
        )
        Text(
            text = " / ${duration.toAppMessageTimeFormat()}",
            style = MaterialTheme.typography.labelSmall,
            color = tintColor.copy(alpha = 0.7f),
        )
      }

      // 2. 中间行：amplitudes

      WaveformSlider(
          progressProvider = {
            val current = positionProvider()
            current.safeDivision(duration).coerceIn(0f, 1f)
          },
          amplitudes = amplitudes,
          tintColor = tintColor,
          onSeek = onSeek,
          stretchToFit = true,
          modifier = Modifier.fillMaxWidth().height(12.dp),
      )
      // 3. 底部占位行：高度与顶部 timeLineHeight 完全一致，构成垂直镜像平衡
      Spacer(modifier = Modifier.height(timeLineHeight))
    }

    // 倍速展示：未点击显示“倍速”，点击后显示数字
    val speedText =
        if (!hasInteractedSpeed) {
          stringResource(R.string.term_media_play_speed)
        } else {
          if (speed % 1f == 0f) "${speed.toInt()}x" else "${speed}x"
        }

    Text(
        text = speedText,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = tintColor,
        modifier =
            Modifier.clip(RoundedCornerShape(4.dp))
                .clickable {
                  hasInteractedSpeed = true
                  onSpeedCycle()
                }
                .padding(horizontal = 8.dp, vertical = 4.dp),
    )
  }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun VideoFullScreenPlayerPreview() {

  ImlogTheme {
    with(PreviewOverlayLayoutScope) {
      VideoFullScreenPlayerContent(
          messageId = MessageId("msg_123"),
          content =
              MessageContentUiModel.Video(
                  amplitudes = listOf(0.2f, 0.5f, 0.8f, 0.4f, 0.9f, 0.3f, 0.6f, 0.7f),
                  storedFilename = "Test.mp4",
                  sourceTemporaryUri = null,
                  thumbnailPath = null,
                  width = 1080,
                  height = 720,
                  duration = 30.seconds,
              ),
          playbackState =
              MediaPlaybackState(
                  status = PlayerStatus.Playing,
                  speed = 1.0f,
              ),
          activePlayPositionHolder = remember { mutableStateOf(20.seconds) },
          inactivePlayPosition = Duration.ZERO,
          onTogglePlay = {},
          onSeek = {},
          onSpeedCycle = {},
          videoSurface = {
            // placeholder
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFF1E1E1E)),
                contentAlignment = Alignment.Center,
            ) {
              Text("Video Surface Preview", color = Color.DarkGray)
            }
          },
      )
    }
  }
}
