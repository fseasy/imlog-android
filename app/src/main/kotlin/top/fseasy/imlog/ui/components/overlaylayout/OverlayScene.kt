package top.fseasy.imlog.ui.components.overlaylayout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import top.fseasy.imlog.ui.components.gesture.blockTouchEvents

@Composable
internal fun <T : Any> OverlayScene(
  item: T,
  itemKey: Any,
  registry: OverlayLayoutMetadataRegistry,
  onDismissFinished: () -> Unit,
  overlayContent: @Composable OverlayLayoutScope.(item: T) -> Unit,
) {
  val coroutineScope = rememberCoroutineScope()

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val currentBounds =
        remember(constraints) {
          Rect(0f, 0f, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        }

    // Bind to `itemKey` solely
    val sceneState =
        remember(itemKey) {
          OverlaySceneState(
              initialItemKey = itemKey,
              initialWindowBounds = currentBounds,
              registry = registry,
              coroutineScope = coroutineScope,
              onDismissFinished = onDismissFinished,
          )
        }

    // Update window bounds once it changed.
    SideEffect {
      sceneState.updateWindowBounds(currentBounds)
    }
    // 启动入场动效
    LaunchedEffect(sceneState) {
      sceneState.runEnterAnimation()
    }

    BackHandler(enabled = sceneState.transitionPhase != OverlayTransitionPhase.Dismissing) {
      sceneState.triggerDismiss()
    }

    val scope = remember(sceneState) { OverlayLayoutScopeImpl(sceneState) }

    // 🌟 1. 转场期间吃掉整屏所有事件，防止动效中乱点打断
    Box(
        modifier =
            Modifier.fillMaxSize()
                .then(
                    if (sceneState.transitionPhase.isTransitioning) Modifier.blockTouchEvents()
                    else Modifier
                )
    ) {
      // 🌟 2. 背景层：支持外部绑定的单击/双击，默认兜底为“单击退出”
      Box(
          modifier =
              Modifier.fillMaxSize()
                  .graphicsLayer {
                    alpha =
                        if (sceneState.transitionPhase == OverlayTransitionPhase.Settled) {
                          sceneState.contentAlphaProvider?.invoke() ?: 1f
                        } else {
                          sceneState.bgAlpha.value
                        }
                  }
                  .background(Color.Black)
                  .pointerInput(
                      sceneState.transitionPhase,
                      sceneState.onBgTap,
                      sceneState.onBgDoubleTap,
                  ) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                          if (sceneState.transitionPhase == OverlayTransitionPhase.Settled) {
                            sceneState.onBgDoubleTap?.invoke(offset)
                          }
                        },
                        onTap = {
                          if (sceneState.transitionPhase == OverlayTransitionPhase.Settled) {
                            // 如果业务方绑定了 Tap 则执行；否则默认行为：单击空白退出
                            sceneState.onBgTap?.invoke() ?: sceneState.triggerDismiss()
                          }
                        },
                    )
                  }
                  // 吞掉黑边所有未被 tap 消费的滑动/拖拽，彻底防止底层列表被滑动
                  .blockTouchEvents()
      )

      Box(
          modifier = Modifier.fillMaxSize().overlayContentTransition(sceneState),
          contentAlignment = Alignment.Center,
      ) {
        scope.overlayContent(item)
      }
    }
  }
}
