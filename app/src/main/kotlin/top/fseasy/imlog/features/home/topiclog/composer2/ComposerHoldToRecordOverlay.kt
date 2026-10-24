package top.fseasy.imlog.features.home.topiclog.composer2

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.fseasy.imlog.R
import top.fseasy.imlog.domain.util.toAppMessageTimeFormat
import top.fseasy.imlog.features.home.topiclog.composer.VoiceRecordingUiState
import top.fseasy.imlog.ui.theme.ImlogTheme
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

private object HoldToSpeakDefaults {
  val ScrimColor = Color(0x99000000)
  val NormalColor = Color.White
  val CancelColor = Color(0xFFEF4444)
  val LockColor = Color(0xFF10B981)
  val BubbleDefaultBg = Color(0xD92C3E50)

  val ShimmerBase = Color(0xB3FFFFFF)
  val ShimmerHighlight = Color(0xFFFFFFFF)
}

/** 业务判定的达成目标 */
enum class HoldToTalkGestureTarget {
  Normal, // 原地录音
  Cancel, // 取消
  Lock, // 向上锁定
}

/** 手势倾向的实时意图（包含 UI 渲染方向） */
enum class HoldToTalkGestureIntent {
  Idle, // 原地/死区内
  Lock, // 向上
  CancelLeft, // 向左取消
  CancelRight, // 向右取消
}

@Immutable
data class GestureEvaluation(
    val intent: HoldToTalkGestureIntent,
    val progress: Float, // 0.0 ~ 1.0 (当前意图达成度)
    val dragOffset: Offset,
) {
  /** 达成目标：progress 达标时映射为对应的业务终态 */
  val confirmedTarget: HoldToTalkGestureTarget
    get() =
        if (progress >= 1f) {
          when (intent) {
            HoldToTalkGestureIntent.Lock -> HoldToTalkGestureTarget.Lock
            HoldToTalkGestureIntent.CancelLeft,
            HoldToTalkGestureIntent.CancelRight -> HoldToTalkGestureTarget.Cancel
            HoldToTalkGestureIntent.Idle -> HoldToTalkGestureTarget.Normal
          }
        } else {
          HoldToTalkGestureTarget.Normal
        }

  companion object {
    val Idle = GestureEvaluation(HoldToTalkGestureIntent.Idle, 0f, Offset.Zero)
  }
}

@Immutable
data class RadialGeometryConfig(
    val deadZoneDp: Dp = 16.dp,
    val triggerDistanceDp: Dp = 56.dp,
    val targetDistanceDp: Dp = 96.dp,
    val bubbleRadiusDp: Dp = 48.dp,
)

@Immutable
data class RadialGeometry(
    val originPosition: Offset,
    val lockTargetPos: Offset,
    val cancelTargetDistancePx: Float,
    private val deadZonePx: Float,
    private val triggerDistPx: Float,
) {
  fun evaluate(currentPos: Offset): GestureEvaluation {
    if (currentPos == Offset.Zero) return GestureEvaluation.Idle

    val dx = currentPos.x - originPosition.x
    val dy = currentPos.y - originPosition.y
    val dist = hypot(dx, dy)
    val rawOffset = Offset(dx, dy)

    if (dist < deadZonePx) {
      return GestureEvaluation(HoldToTalkGestureIntent.Idle, 0f, rawOffset)
    }

    val absDx = abs(dx)
    val absDy = abs(dy)
    val effectiveRangePx = (triggerDistPx - deadZonePx).coerceAtLeast(1f)

    return when {
      dy < 0 && absDy > absDx -> {
        val progress = ((-dy - deadZonePx) / effectiveRangePx).coerceIn(0f, 1f)
        GestureEvaluation(HoldToTalkGestureIntent.Lock, progress, rawOffset)
      }
      dx < 0 && absDx >= absDy -> {
        val progress = ((-dx - deadZonePx) / effectiveRangePx).coerceIn(0f, 1f)
        GestureEvaluation(HoldToTalkGestureIntent.CancelLeft, progress, rawOffset)
      }
      dx > 0 && absDx >= absDy -> {
        val progress = ((dx - deadZonePx) / effectiveRangePx).coerceIn(0f, 1f)
        GestureEvaluation(HoldToTalkGestureIntent.CancelRight, progress, rawOffset)
      }
      else -> GestureEvaluation(HoldToTalkGestureIntent.Idle, 0f, rawOffset)
    }
  }

  companion object {
    fun create(
        originPosition: Offset,
        density: Density,
        config: RadialGeometryConfig = RadialGeometryConfig(),
    ): RadialGeometry {
      val targetDistPx = with(density) { config.targetDistanceDp.toPx() }
      val bubbleRadiusPx = with(density) { config.bubbleRadiusDp.toPx() }
      val deadZonePx = with(density) { config.deadZoneDp.toPx() }
      val triggerDistPx = with(density) { config.triggerDistanceDp.toPx() }

      val lockTargetPos =
          Offset(
              x = originPosition.x,
              y = (originPosition.y - targetDistPx).coerceAtLeast(bubbleRadiusPx + 40f),
          )

      return RadialGeometry(
          originPosition = originPosition,
          lockTargetPos = lockTargetPos,
          cancelTargetDistancePx = targetDistPx * 0.85f,
          deadZonePx = deadZonePx,
          triggerDistPx = triggerDistPx,
      )
    }
  }
}

@Composable
fun HoldToSpeakOverlay(
    geometry: RadialGeometry?,
    evaluationProvider: () -> GestureEvaluation,
    voiceRecodingUiStateHolder: State<VoiceRecordingUiState>,
    modifier: Modifier = Modifier,
) {
  if (geometry == null) return

  val primaryColor = MaterialTheme.colorScheme.primary
  val hapticFeedback = LocalHapticFeedback.current
  val density = LocalDensity.current
  val comfortableGapPx = remember(density) { with(density) { 52.dp.toPx() } }
  val maxStretchPx = remember(density) { with(density) { 18.dp.toPx() } }

  // 达成目标驱动的离散状态更新（低频重组）
  val confirmedTarget by remember {
    derivedStateOf { evaluationProvider().confirmedTarget }
  }

  // 震动反馈
  var lastTarget by remember { mutableStateOf(HoldToTalkGestureTarget.Normal) }
  LaunchedEffect(confirmedTarget) {
    if (confirmedTarget != lastTarget) {
      if (confirmedTarget != HoldToTalkGestureTarget.Normal) {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
      }
      lastTarget = confirmedTarget
    }
  }

  // 全局状态色彩过渡
  val statusColor by
      animateColorAsState(
          targetValue =
              when (confirmedTarget) {
                HoldToTalkGestureTarget.Cancel -> HoldToSpeakDefaults.CancelColor
                HoldToTalkGestureTarget.Lock -> HoldToSpeakDefaults.LockColor
                HoldToTalkGestureTarget.Normal -> HoldToSpeakDefaults.NormalColor
              },
          label = "StatusColor",
      )

  // 原点单圆弹性入场动画
  var anchorStarted by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) { anchorStarted = true }
  val anchorScale by
      animateFloatAsState(
          targetValue = if (anchorStarted) 1f else 0f,
          animationSpec =
              spring(
                  dampingRatio = Spring.DampingRatioMediumBouncy,
                  stiffness = Spring.StiffnessMediumLow,
              ),
          label = "AnchorScale",
      )

  // 向上小箭头微跳
  val chevronTransition = rememberInfiniteTransition(label = "ChevronTransition")
  val chevronBounceY by
      chevronTransition.animateFloat(
          initialValue = 0f,
          targetValue = -6f,
          animationSpec =
              infiniteRepeatable(
                  animation = tween(600, easing = FastOutSlowInEasing),
                  repeatMode = RepeatMode.Reverse,
              ),
          label = "ChevronBounceY",
      )

  Box(modifier = modifier.fillMaxSize().background(HoldToSpeakDefaults.ScrimColor)) {
    // 1. 原点底槽与牵引核心（全在 drawBehind 阶段直接通过 evaluationProvider 采样，避免 Composition 重组）
    Spacer(
        modifier =
            Modifier.fillMaxSize().drawBehind {
              if (anchorScale <= 0.01f) return@drawBehind

              val eval = evaluationProvider()
              val overallProgress = eval.progress

              // 阻尼位移计算
              val rawDrag = eval.dragOffset
              val dragDist = hypot(rawDrag.x, rawDrag.y)
              val clampedDist = (dragDist * 0.22f).coerceAtMost(maxStretchPx)
              val directionUnit = if (dragDist > 0.001f) rawDrag / dragDist else Offset.Zero
              val elasticOffset = directionUnit * clampedDist

              val decay = overallProgress.coerceIn(0f, 1f)
              val dynamicScale = (1f - decay * 0.45f) * anchorScale
              val dynamicAlpha = 1f - decay * 0.75f

              // A. 底槽印记
              drawCircle(
                  color = primaryColor.copy(alpha = 0.08f * anchorScale),
                  radius = 24.dp.toPx() * anchorScale,
                  center = geometry.originPosition,
              )
              // B. 牵引核
              drawCircle(
                  color = primaryColor.copy(alpha = dynamicAlpha),
                  radius = 24.dp.toPx() * dynamicScale,
                  center = geometry.originPosition + elasticOffset,
              )
            }
    )

    // 2. 顶部状态信息看板
    TopDashboard(
        voiceUiState = voiceRecodingUiStateHolder.value,
        statusColor = statusColor,
        currentTarget = confirmedTarget,
        modifier = Modifier.align(Alignment.TopCenter).offset(y = 100.dp),
    )

    // 3. Lock 目标气泡
    LockTargetComponent(
        lockTargetPos = geometry.lockTargetPos,
        lockProgressProvider = {
          val eval = evaluationProvider()
          if (eval.intent == HoldToTalkGestureIntent.Lock) eval.progress else 0f
        },
        isConfirmed = confirmedTarget == HoldToTalkGestureTarget.Lock,
        suppressProgressProvider = {
          val eval = evaluationProvider()
          if (
              eval.intent == HoldToTalkGestureIntent.CancelLeft ||
                  eval.intent == HoldToTalkGestureIntent.CancelRight
          )
              eval.progress
          else 0f
        },
    )

    // 向上引导小箭头
    val chevronCenterY = (geometry.lockTargetPos.y + geometry.originPosition.y) / 2f
    Icon(
        imageVector = Icons.Rounded.KeyboardArrowUp,
        contentDescription = null,
        tint = HoldToSpeakDefaults.NormalColor.copy(alpha = 0.5f),
        modifier =
            Modifier.offset {
                  IntOffset(
                      x = (geometry.originPosition.x - 12.dp.toPx()).roundToInt(),
                      y = (chevronCenterY - 12.dp.toPx() + chevronBounceY.dp.toPx()).roundToInt(),
                  )
                }
                .size(24.dp)
                .graphicsLayer {
                  val eval = evaluationProvider()
                  val activeProg =
                      if (eval.intent != HoldToTalkGestureIntent.Idle) eval.progress else 0f
                  alpha = (1f - activeProg * 2.5f).coerceIn(0f, 1f)
                },
    )

    // 4. 左/右双翼文字指引（初始均展示，随着手势 progress 激活统一快速淡出）
    val cancelText = stringResource(R.string.composer_hold_to_record_cancel)

    GentleShimmerWing(
        displayText = "‹ ‹ $cancelText",
        isRightWing = false,
        originPosition = geometry.originPosition,
        gapPx = comfortableGapPx,
        overallFadeOutProgressProvider = { evaluationProvider().progress },
    )

    GentleShimmerWing(
        displayText = "$cancelText › ›",
        isRightWing = true,
        originPosition = geometry.originPosition,
        gapPx = comfortableGapPx,
        overallFadeOutProgressProvider = { evaluationProvider().progress },
    )

    // 5. 动态响应 Close 气泡（仅在滑向对应侧时浮现）
    DynamicCloseTarget(
        targetCenter =
            Offset(
                geometry.originPosition.x - geometry.cancelTargetDistancePx,
                geometry.originPosition.y,
            ),
        activeProgressProvider = {
          val eval = evaluationProvider()
          if (eval.intent == HoldToTalkGestureIntent.CancelLeft) eval.progress else 0f
        },
        isConfirmed =
            confirmedTarget == HoldToTalkGestureTarget.Cancel &&
                evaluationProvider().intent == HoldToTalkGestureIntent.CancelLeft,
    )

    DynamicCloseTarget(
        targetCenter =
            Offset(
                geometry.originPosition.x + geometry.cancelTargetDistancePx,
                geometry.originPosition.y,
            ),
        activeProgressProvider = {
          val eval = evaluationProvider()
          if (eval.intent == HoldToTalkGestureIntent.CancelRight) eval.progress else 0f
        },
        isConfirmed =
            confirmedTarget == HoldToTalkGestureTarget.Cancel &&
                evaluationProvider().intent == HoldToTalkGestureIntent.CancelRight,
    )
  }
}

/** 顶部信息看板组件 */
@Composable
private fun TopDashboard(
    voiceUiState: VoiceRecordingUiState,
    statusColor: Color,
    currentTarget: HoldToTalkGestureTarget,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier = modifier,
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
        text = voiceUiState.elapsed.toAppMessageTimeFormat(),
        color = statusColor,
        fontSize = 36.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
    )
    Spacer(modifier = Modifier.height(10.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      repeat(5) {
        Box(
            modifier =
                Modifier.width(4.dp)
                    .height(18.dp)
                    .background(statusColor.copy(alpha = 0.85f), CircleShape)
        )
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    AnimatedContent(
        targetState = currentTarget,
        transitionSpec = {
          (slideInVertically { it / 2 } + fadeIn()) togetherWith
              (slideOutVertically { -it / 2 } + fadeOut())
        },
        label = "StatusTextTransition",
    ) { target ->
      val tipText =
          when (target) {
            HoldToTalkGestureTarget.Normal ->
                stringResource(R.string.composer_hold_to_record_status_recording)
            HoldToTalkGestureTarget.Cancel ->
                stringResource(R.string.composer_hold_to_record_status_release_cancel)
            HoldToTalkGestureTarget.Lock ->
                stringResource(R.string.composer_hold_to_record_status_release_lock)
          }
      Text(
          text = tipText,
          color = statusColor,
          fontSize = 13.sp,
          fontWeight = FontWeight.Medium,
          letterSpacing = 0.5.sp,
      )
    }
  }
}

@Composable
private fun LockTargetComponent(
    lockTargetPos: Offset,
    lockProgressProvider: () -> Float,
    isConfirmed: Boolean,
    suppressProgressProvider: () -> Float,
    modifier: Modifier = Modifier,
) {
  val lockProg = lockProgressProvider()

  val scaleFactor by
      animateFloatAsState(
          targetValue = if (isConfirmed) 1.25f else (1f + 0.1f * lockProg),
          animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
          label = "LockScale",
      )

  val bgColor by
      animateColorAsState(
          targetValue =
              if (isConfirmed) HoldToSpeakDefaults.LockColor
              else HoldToSpeakDefaults.BubbleDefaultBg,
          animationSpec = tween(durationMillis = 150),
          label = "LockBgColor",
      )

  Box(
      modifier =
          modifier
              .offset {
                IntOffset(
                    x = (lockTargetPos.x - 24.dp.toPx()).roundToInt(),
                    y = (lockTargetPos.y - 24.dp.toPx()).roundToInt(),
                )
              }
              .size(48.dp)
              .graphicsLayer {
                val suppress = (suppressProgressProvider() * 2.5f).coerceIn(0f, 1f)
                alpha = 1f - suppress
                scaleX = scaleFactor
                scaleY = scaleFactor
              },
      contentAlignment = Alignment.Center,
  ) {
    Box(
        modifier =
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = if (isConfirmed) 1f else lockProg }
                .background(bgColor, CircleShape)
    )

    Icon(
        imageVector = Icons.Rounded.Lock,
        contentDescription = stringResource(R.string.composer_hold_to_record_lock),
        modifier = Modifier.size(22.dp),
        tint =
            if (isConfirmed) Color.White
            else HoldToSpeakDefaults.NormalColor.copy(alpha = 0.6f + 0.4f * lockProg),
    )
  }
}

@Composable
private fun DynamicCloseTarget(
    targetCenter: Offset,
    activeProgressProvider: () -> Float,
    isConfirmed: Boolean,
    modifier: Modifier = Modifier,
) {
  val activeProgress = activeProgressProvider()
  if (activeProgress <= 0.01f) return

  val targetScale by
      animateFloatAsState(
          targetValue =
              when {
                isConfirmed -> 1.25f
                else -> 0.6f + 0.4f * activeProgress
              },
          animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
          label = "CloseTargetScale",
      )

  val bgColor by
      animateColorAsState(
          targetValue =
              if (isConfirmed) HoldToSpeakDefaults.CancelColor
              else HoldToSpeakDefaults.BubbleDefaultBg,
          animationSpec = tween(durationMillis = 150),
          label = "CloseTargetBgColor",
      )

  val appearAlpha = (activeProgress * 2f).coerceIn(0f, 1f)

  Box(
      modifier =
          modifier
              .offset {
                IntOffset(
                    x = (targetCenter.x - 24.dp.toPx()).roundToInt(),
                    y = (targetCenter.y - 24.dp.toPx()).roundToInt(),
                )
              }
              .size(48.dp)
              .graphicsLayer {
                alpha = appearAlpha
                scaleX = targetScale
                scaleY = targetScale
              }
              .background(bgColor, CircleShape),
      contentAlignment = Alignment.Center,
  ) {
    Icon(
        imageVector = Icons.Rounded.Close,
        contentDescription = stringResource(R.string.composer_hold_to_record_cancel),
        tint = Color.White,
        modifier = Modifier.size(24.dp),
    )
  }
}

@Composable
private fun GentleShimmerWing(
    displayText: String,
    isRightWing: Boolean,
    originPosition: Offset,
    gapPx: Float,
    overallFadeOutProgressProvider: () -> Float,
    modifier: Modifier = Modifier,
) {
  var textWidthPx by remember { mutableIntStateOf(0) }
  var textHeightPx by remember { mutableIntStateOf(0) }

  val shimmerTransition = rememberInfiniteTransition(label = "GentleWingShimmer")
  val shimmerProgress by
      shimmerTransition.animateFloat(
          initialValue = 0f,
          targetValue = 1f,
          animationSpec =
              infiniteRepeatable(
                  animation =
                      keyframes {
                        durationMillis = 2600
                        0.0f at 0
                        1.0f at 800 using FastOutSlowInEasing
                        1.0f at 2600
                      },
                  repeatMode = RepeatMode.Restart,
              ),
          label = "ShimmerFraction",
      )

  val currentWidth = if (textWidthPx > 0) textWidthPx.toFloat() else 100f
  val bandWidthPx = currentWidth * 0.8f

  val (startOffset, endOffset) =
      if (isRightWing) {
        val sweepStart = (currentWidth + bandWidthPx) * shimmerProgress - bandWidthPx
        Offset(sweepStart, 0f) to Offset(sweepStart + bandWidthPx, 0f)
      } else {
        val sweepStart = (currentWidth + bandWidthPx) * (1f - shimmerProgress)
        Offset(sweepStart, 0f) to Offset(sweepStart - bandWidthPx, 0f)
      }

  val shimmerBrush =
      Brush.linearGradient(
          colors =
              listOf(
                  HoldToSpeakDefaults.ShimmerBase,
                  HoldToSpeakDefaults.ShimmerHighlight,
                  HoldToSpeakDefaults.ShimmerBase,
              ),
          start = startOffset,
          end = endOffset,
      )

  Text(
      text = displayText,
      style =
          TextStyle(
              brush = shimmerBrush,
              fontSize = 13.sp,
              fontWeight = FontWeight.Medium,
              letterSpacing = 0.8.sp,
          ),
      modifier =
          modifier
              .onSizeChanged {
                textWidthPx = it.width
                textHeightPx = it.height
              }
              .offset {
                val x =
                    if (isRightWing) {
                      (originPosition.x + gapPx).roundToInt()
                    } else {
                      (originPosition.x - gapPx - textWidthPx).roundToInt()
                    }
                val y = (originPosition.y - textHeightPx / 2f).roundToInt()
                IntOffset(x, y)
              }
              .graphicsLayer {
                val overallProgress = overallFadeOutProgressProvider()
                val fadeOut = (overallProgress * 3.3f).coerceIn(0f, 1f)
                alpha = if (textWidthPx == 0) 0f else (1f - fadeOut)
              },
  )
}

@Preview(name = "Normal Rest State", showBackground = true)
@Preview(name = "Dark Mode", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LocalRadialHoldToRecordPreview() {
  ImlogTheme {
    Surface {
      val density = LocalDensity.current
      val geometry =
          RadialGeometry.create(
              originPosition = Offset(500f, 1300f),
              density = density,
          )
      val voiceRecordingUiState = remember {
        mutableStateOf(VoiceRecordingUiState(elapsed = 2400.milliseconds))
      }
      HoldToSpeakOverlay(
          geometry = geometry,
          evaluationProvider = { GestureEvaluation.Idle },
          voiceRecodingUiStateHolder = voiceRecordingUiState,
      )
    }
  }
}
