package top.fseasy.imlog.ui.components

import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import top.fseasy.imlog.R
import top.fseasy.imlog.ui.theme.errorSmall


@Composable
fun InternalErrorInfoText(
    errorMessage: String,
) {
    Text(
        "${stringResource(R.string.internal_error_head)}: $errorMessage",
        style = MaterialTheme.typography.errorSmall
    )
}


data class HighlightConfig(
    val color: Color = Color(0xFFFFD54F),
    val scale: Float = 1.0f,
    val glowColor: Color = Color(0xFFFFD54F).copy(alpha = 0.4f),
)

@Composable
fun HighlightedText(
    text: String,
    style: TextStyle,
    highlight: HighlightConfig,
    textAlign: TextAlign = TextAlign.Center,
) {
    val glowAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // 呼吸光效
        while (true) {
            glowAlpha.animateTo(0.8f, animationSpec = tween(1500))
            glowAlpha.animateTo(0.3f, animationSpec = tween(1500))
        }
    }

    // 将缩放和透明度统一交给 graphicsLayer 管理，避免布局尺寸改变导致重排或错位
    val baseModifier = Modifier.graphicsLayer {
        scaleX = highlight.scale
        scaleY = highlight.scale
    }

    Box(
        modifier = Modifier.wrapContentSize(), contentAlignment = Alignment.Center
    ) {
        // 发光层
        if (highlight.glowColor != Color.Unspecified) {
            Text(
                text = text, style = style.copy(
                    color = highlight.glowColor,
                    shadow = Shadow(
                        color = highlight.glowColor, offset = Offset(0f, 0f), blurRadius = 20f
                    ),
                ), textAlign = textAlign, modifier = baseModifier.graphicsLayer {
                    alpha = glowAlpha.value
                    // 使用 Compose 的 asComposeRenderEffect 包装器
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        renderEffect = android.graphics.RenderEffect.createBlurEffect(
                            20f, 20f, Shader.TileMode.CLAMP
                        )
                            .asComposeRenderEffect()
                    }
                })
        }

        // 主文本
        Text(
            text = text, style = style.copy(
                color = if (highlight.color != Color.Unspecified) highlight.color else style.color
            ), textAlign = textAlign, modifier = baseModifier
        )
    }
}