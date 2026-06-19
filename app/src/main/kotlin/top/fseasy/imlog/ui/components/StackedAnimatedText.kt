package top.fseasy.imlog.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay

data class AnimatedTextLineConfig(
    val text: String,
    val style: TextStyle,
    val alignment: TextAlign = TextAlign.Center,
    val durationMs: Long = 1000,          // 该阶段动画持续时间
    val stayDurationMs: Long = 0,         // 动画完成后停留时间
    val highlight: HighlightConfig? = null, // 高亮配置（仅当前阶段有效）
    val enterTransition: EnterTransition? = null, // 自定义进入动画
    val exitTransition: ExitTransition? = null,    // 自定义退出动画
)

data class StackedAnimationTiming(
    val initialDelay: Long = 200,
    val lineGap: Long = 500,  // 行与行之间的间隔
    val overlap: Boolean = false, // 是否叠加（前一行不完全消失后一行才出现）
)

fun defaultEnterTransition(): EnterTransition {
    return fadeIn(animationSpec = tween(600)) + slideInVertically(
        initialOffsetY = { it / 3 }, animationSpec = tween(600, easing = FastOutSlowInEasing)
    ) + scaleIn(
        initialScale = 0.8f, animationSpec = tween(600)
    )
}

fun defaultExitTransition(): ExitTransition {
    return fadeOut(animationSpec = tween(500)) + slideOutVertically(
        targetOffsetY = { -it / 4 }, animationSpec = tween(500)
    ) + scaleOut(
        targetScale = 0.9f, animationSpec = tween(500)
    )
}

@Composable
fun StackedAnimatedText(
    textLines: List<AnimatedTextLineConfig>,
    modifier: Modifier = Modifier,
    timingConfig: StackedAnimationTiming = StackedAnimationTiming(),
    onAnimationComplete: () -> Unit = {},
) {
    var currentPhase by remember { mutableIntStateOf(-1) }

    LaunchedEffect(Unit) {
        var accumulatedDelay = timingConfig.initialDelay

        textLines.forEachIndexed { index, config ->
            // 延迟到该阶段的开始时间
            delay(accumulatedDelay)
            currentPhase = index
            accumulatedDelay += config.durationMs

            // 如果配置了停留时间，在该阶段停留
            if (config.stayDurationMs > 0) {
                delay(config.stayDurationMs)
            }
        }

        onAnimationComplete()
    }

    Box(
        modifier = modifier, contentAlignment = Alignment.Center
    ) {
        textLines.forEachIndexed { index, config ->
            val isVisible = currentPhase >= index
            val isActive = currentPhase == index

            val enterTransition = config.enterTransition ?: defaultEnterTransition()
            val exitTransition = config.exitTransition ?: defaultExitTransition()

            AnimatedVisibility(
                visible = isVisible, enter = enterTransition, exit = exitTransition
            ) {
                if (isActive && config.highlight != null) {
                    HighlightedText(
                        text = config.text,
                        style = config.style,
                        highlight = config.highlight,
                        textAlign = config.alignment
                    )
                } else {
                    Text(
                        text = config.text, style = config.style, textAlign = config.alignment
                    )
                }
            }
        }
    }
}

