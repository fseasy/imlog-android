package top.fseasy.imlog.ui.components.overlaylayout

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import top.fseasy.imlog.ui.components.gesture.TransformHandoverChain

interface OverlayLayoutScope {

  val transitionPhase: OverlayTransitionPhase

  /**
   * 手动触发退出
   *
   * @param itemKey you can set it to null if it's not in geometry mode
   */
  fun dismiss(itemKey: Any? = null)

  /**
   * 🌟 核心：挂载到手势联动组件上的专属 Modifier。 内部通过 Lambda 实时读取手势状态，不产生重组，天然随组件卸载而解绑！
   *
   * @param itemKey 多图翻页时当前图片的 key（可选，单图无需传）
   * @param backgroundAlphaProvider 联动背景透明度（0f ~ 1f）
   * @param dismissTransformHandoverProvider 如果内层有手势 transform，在执行退出动画前运行该函数做 transform handover
   */
  fun Modifier.overlayInteractiveTarget(
      itemKey: Any? = null,
      backgroundAlphaProvider: (() -> Float)?,
      dismissTransformHandoverProvider: (() -> TransformHandoverChain)?,
  ): Modifier

  /** 全屏背景手势统一配置（不配置则 do nothing） */
  fun configureBackgroundTap(
      onTap: (() -> Unit)? = null,
      onDoubleTap: ((Offset) -> Unit)? = null,
  )
}

/** [OverlayLayoutScope] 的具体实现，负责桥接 UI 配置与底层 [OverlaySceneState] */
internal class OverlayLayoutScopeImpl(
    private val state: OverlaySceneState,
) : OverlayLayoutScope {

  override val transitionPhase: OverlayTransitionPhase
    get() = state.transitionPhase

  override fun dismiss(itemKey: Any?) {
    state.triggerDismiss(
        itemKey,
    )
  }

  override fun configureBackgroundTap(onTap: (() -> Unit)?, onDoubleTap: ((Offset) -> Unit)?) {
    state.onBgTap = onTap
    state.onBgDoubleTap = onDoubleTap
  }

  override fun Modifier.overlayInteractiveTarget(
      itemKey: Any?,
      backgroundAlphaProvider: (() -> Float)?,
      dismissTransformHandoverProvider: (() -> TransformHandoverChain)?,
  ): Modifier =
      this.then(
          OverlayInteractiveTargetElement(
              itemKey = itemKey,
              backgroundAlphaProvider = backgroundAlphaProvider,
              dismissTransformHandoverProvider = dismissTransformHandoverProvider,
              state = state,
          )
      )
}

/** 描述器：使用 data class 自动生成 equals/hashCode， Compose 借此自动判断参数是否有变，并在需要时触发 node.update()。 */
internal data class OverlayInteractiveTargetElement(
    val itemKey: Any?,
    val backgroundAlphaProvider: (() -> Float)?,
    val dismissTransformHandoverProvider: (() -> TransformHandoverChain)?,
    val state: OverlaySceneState,
) : ModifierNodeElement<OverlayInteractiveTargetNode>() {

  override fun create(): OverlayInteractiveTargetNode {
    return OverlayInteractiveTargetNode(
        itemKey = itemKey,
        backgroundAlphaProvider = backgroundAlphaProvider,
        dismissTransformHandoverProvider = dismissTransformHandoverProvider,
        state = state,
    )
  }

  override fun update(node: OverlayInteractiveTargetNode) {
    node.update(
        itemKey = itemKey,
        backgroundAlpha = backgroundAlphaProvider,
        dismissTransformHandover = dismissTransformHandoverProvider,
    )
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "overlayInteractiveTarget"
    properties["itemKey"] = itemKey
    properties["backgroundAlpha"] = backgroundAlphaProvider
    properties["dismissTransformHandover"] = dismissTransformHandoverProvider
  }
}

/** 🌟 核心节点：直接绑定在 Compose 的 LayoutNode 上， 拥有原生的生命周期（onAttach / onDetach），完全脱离 Composable 重组槽位。 */
internal class OverlayInteractiveTargetNode(
    var itemKey: Any?,
    var backgroundAlphaProvider: (() -> Float)?,
    var dismissTransformHandoverProvider: (() -> TransformHandoverChain)?,
    private val state: OverlaySceneState,
) : Modifier.Node() {

  /** 节点挂载进 UI 树时触发（相当于 DisposableEffect 的初次进入） */
  override fun onAttach() {
    syncToState()
  }

  /** 每次重组时，如果 Element 判定参数变化，会调用此方法。 这里直接同步最新参数给全局 state，零延迟，无需任何 rememberUpdatedState！ */
  fun update(
      itemKey: Any?,
      backgroundAlpha: (() -> Float)?,
      dismissTransformHandover: (() -> TransformHandoverChain)?,
  ) {
    this.itemKey = itemKey
    this.backgroundAlphaProvider = backgroundAlpha
    this.dismissTransformHandoverProvider = dismissTransformHandover

    if (isAttached) {
      syncToState()
    }
  }

  /** 节点从 UI 树卸载时触发（相当于 onDispose） */
  override fun onDetach() {
    val effectiveKey = itemKey ?: state.initialItemKey
    // 🌟 防误杀机制：只有当前全局激活的依然是我自己，才清理
    if (state.currentItemKeyProvider?.invoke() == effectiveKey) {
      state.currentItemKeyProvider = null
      state.contentAlphaProvider = null
      state.dismissTransformHandoverProvider = null
    }
  }

  private fun syncToState() {
    val effectiveKey = itemKey ?: state.initialItemKey
    state.currentItemKeyProvider = { effectiveKey }
    state.contentAlphaProvider = backgroundAlphaProvider
    state.dismissTransformHandoverProvider = dismissTransformHandoverProvider
  }
}
