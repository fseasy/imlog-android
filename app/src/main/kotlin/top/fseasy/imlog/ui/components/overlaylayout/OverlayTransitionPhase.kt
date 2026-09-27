package top.fseasy.imlog.ui.components.overlaylayout

enum class OverlayTransitionPhase {
  Entering, // 入场展开动效中
  Settled, // 稳定浏览交互中
  Dismissing; // 退场飞回动效中

  val isTransitioning: Boolean
    get() = this != Settled
}