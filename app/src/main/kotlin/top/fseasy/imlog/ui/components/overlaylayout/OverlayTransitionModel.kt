package top.fseasy.imlog.ui.components.overlaylayout

enum class OverlayTransitionPhase {
  Entering, // 入场展开动效中
  Settled, // 稳定浏览交互中
  Dismissing; // 退场飞回动效中

  val isTransitioning: Boolean
    get() = this != Settled
}

enum class OverlayTransitionMode {
  /**
   * 几何/共享元素模式：
   * 关联缩略图（Thumbnail），从缩略图 Rect 展开至全屏，退出时还原裁切、圆角和位置。
   * 适用场景：图片查看器、视频播放器、卡片展开。
   */
  Geometry,

  /**
   * 全屏渐变模式：
   * 内容保持全屏，仅通过 Alpha 硬件图层执行平滑淡入、淡出。
   * 适用场景：全屏选词面板、纯文本预览、沉浸式全屏弹窗。
   */
  Fade,

  // 🌟 未来若需要扩展：
  // SlideUp, // 类似 BottomSheet 从底部向上滑出
  // None,    // 瞬间显示/隐藏，无动效
}