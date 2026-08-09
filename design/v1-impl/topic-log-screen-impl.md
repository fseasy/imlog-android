## 如何实现点击消息列表里图片、视频的缩略图，动画 transition 到全屏，然后展示原图/播放视频？


需要使用 Shared element transitions， 官方文档见： https://developer.android.com/develop/ui/compose/animation/shared-elements

主要逻辑就是：

1. 定义 shared scope 
2. 定义 animated content
3. 在消息列表的缩略图元素，和全屏页面，绑定上二者(key 包含 messageId 从而实现绑定)
4. 在顶层(整个 content, 也就是 消息列表+编辑器)之上，新增一个 transition scope + animatedContent, 并交叠这个 content 和 全屏页面
   1. 因为全屏页面也要共享 player，所以整个 play 的状态要全部提升到顶级的 viewmodel了，然后再往下透传
   2. 当然也要透传 scope
   3. 也尝试问了下用 CompositionLocal ，这个文档里是说可以用，但这有让我进一步想岂不是 player 这些也可以，进一步 viewModel 也可以？
      1. 所以还是说这个并不是一个好的范式—别了，就透传呗，没多深 


gemini 的窗口： https://aistudio.google.com/prompts/1YhAAW3lgVD4gSB0jG3d4M6ulFBE71mo_


还有我的疑问：

> 这两个绑定元素的生命周期互斥的吧，怎么实现绑定的呢？是说 animatedContent 中间态的时候，两个元素都会渲染吗？
> 我的疑问是，在点击之后，全屏元素——也就是绑定的 shared element 才被创建出来，这时还能处理 transition 吗？这个是预期的吗


Gemini 3.6 Flash 的答案如下：

**是的，这完全是预期的，而且这正是 Compose 共享元素动画最精妙的核心机制！**



---

问题一：这两个绑定元素的生命周期互斥吗？中间态两个元素都会渲染吗？

**结论：在动画过渡的“中间态（Transition 期间）”，两个元素是【同时存在】于组件树中的。**

 `AnimatedContent`（或 `AnimatedVisibility`）的真实工作机制是：

1. 当状态从 `null` 变成 `MediaInput` 时，`AnimatedContent` **不会立刻卸载旧的页面（列表）**。
2. 在动画播放的这几百毫秒内（比如 300ms），`AnimatedContent` 的内部会**同时保留并渲染“旧页面（Exit）”和“新页面（Enter）”**。
3. 只有当过渡动画**彻底播放结束**的那一刻，`AnimatedContent` 才会将旧页面从 Composition 树中销毁（Dispose）。

因此，在动画进行的这 300ms 中间态里，旧的缩略图和新的全屏图**同时存在**，这就为共享元素动画提供了前提。

---

问题二：点击之后全屏元素才刚被创建出来，还能处理 Transition 吗？

**结论：完全能，而且这正是 Compose 测量与布局（Measure & Layout）阶段的威力！**

你可以把 `SharedTransitionLayout` 想象成一个**“全局裁判”**。它的运作时序如下（全部发生在极短的几毫秒内）：

#### 帧 0（点击发生的瞬间，组件刚创建）：
1. 用户点击列表项，全屏 `FullScreenMediaContainer` **刚被创建**并加入组件树。
2. 全屏元素里的 `AsyncImage` 执行到 `sharedElement(...)`，向外层的“裁判” `SharedTransitionScope` 注册了它的 Key：`"media_123"`。
3. 此时“裁判”发现：“**Hey！树里现在有两个组件使用了同一个 Key（"media_123"）！**”

#### 帧 1（测量与首帧准备阶段，还没有画到屏幕上）：
Compose 开始进行正常的 Measure & Layout（测量与布局）：
* **测量旧元素**：拿到列表缩略图当前在屏幕上的绝对坐标 `RectA`（比如 `x: 100, y: 500, w: 120, h: 160`）。
* **测量新元素**：虽然新元素刚创建，但 Compose 会立即对其进行全屏布局测量，拿到它在全屏状态下的目标坐标 `RectB`（比如 `x: 0, y: 0, w: 1080, h: 2400`）。

#### 动画启动（1 ~ 300ms）：
“裁判”已经拿到了**起点 `RectA`** 和 **终点 `RectB`**：
1. **隐藏原节点**：“裁判”会暂时把旧节点和新节点的常规绘制隐藏，防止两个图片重叠乱画。
2. **在 Overlay 绘制**：在最顶层画板上，根据动画进度（0.0 $\rightarrow$ 1.0），计算出每一帧的中间矩形 `Rect(t)`，并将图片裁剪、拉伸、平移绘制在这个中间矩形上。

#### 动画结束（300ms 后）：
* 动画完成，`AnimatedContent` 彻底卸载旧页面。
* “裁判”交还绘制权，新元素（全屏图/视频）恢复正常绘制。


整体 UI 的层次：

+-------------------------------------------------------------------+
| SharedTransitionLayout (最外层)                                    |
|  +-------------------------------------------------------------+  |
|  | Box (根容器，fillMaxSize)                                    |  |
|  |                                                             |  |
|  |  [1. 底层] Scaffold (聊天页面)                               |  |
|  |     ├── topBar (标题栏: "张三")                             |  |
|  |     ├── content (聊天消息列表 LazyColumn)                    |  |
|  |     └── bottomBar (底部输入框)                               |  |
|  |                                                             |  |
|  |  [2. 顶层] FullScreenMediaContainer (全屏预览 Overlay)        |  |
|  |     └── 覆盖掉整个 Scaffold (包含 TopBar 和 BottomBar)      |  |
|  +-------------------------------------------------------------+  |
+-------------------------------------------------------------------+


代码

```kotlin
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ChatScreen(
    mediaPlayerStateHolder: MediaPlayerStateHolder
) {
    // 记录当前点击放大的媒体 (null 表示未全屏预览)
    var selectedMedia by remember { mutableStateOf<MediaInput?>(null) }

    // 1. 最外层：SharedTransitionLayout 负责动画上下文
    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
        
        // 2. 根 Box：控制【聊天页】和【全屏预览页】的上下叠放层级
        Box(modifier = Modifier.fillMaxSize()) {

            // ================= 【第 1 层：底层 Scaffold 页面】 =================
            Scaffold(
                topBar = {
                    TopAppBar(title = { Text("张三") })
                },
                bottomBar = {
                    ChatInputBottomBar() // 底部输入框
                },
                modifier = Modifier.fillMaxSize()
            ) { innerPadding ->
                // 聊天消息列表
                ChatMessageList(
                    modifier = Modifier.padding(innerPadding),
                    onMediaClick = { media -> selectedMedia = media },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@AnimatedVisibilityScope // 如果有的话
                )
            }

            // ================= 【第 2 层：顶层全屏预览 Overlay】 =================
            // 使用 AnimatedVisibility 控制全屏蒙层的渐显/渐隐，并提供 AnimatedVisibilityScope
            AnimatedVisibility(
                visible = selectedMedia != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                selectedMedia?.let { media ->
                    FullScreenMediaContainer(
                        mediaInput = media,
                        mediaPlayerStateHolder = mediaPlayerStateHolder,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@AnimatedVisibility, // 传递当前动画 Scope
                        onClose = { selectedMedia = null } // 关闭时置 null，隐藏顶层
                    )
                }
            }
        }
    }
}
```

AnimatedVisibility 适合 “开 / 关” 布尔显隐（只有“出现”和“消失”两种状态）。
AnimatedContent 适合 “状态 A → 状态 B” 内容替换（除了开/关，还支持在不同内容间直接渐变切换）。

对于微信/Telegram 级别的 IM 媒体查看器，最稳健、可扩展性最好的架构是：
- 外层用 AnimatedVisibility 负责全屏 Overlay 的**“打开与关闭”**。
- 全屏内部如果有左右滑动查看同聊天记录其他图片/视频的需求，内部放入 Compose 官方的 HorizontalPager。