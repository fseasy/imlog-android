package top.fseasy.imlog.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EventScope(private val scope: CoroutineScope) : CoroutineScope by scope {
  private val jobs = mutableMapOf<Any, Job>()

  /** 定义一个 launchLatest: 如果一个 action 需要新的事件顶替旧事件(典型的就是 snackbar), 就用这个函数来 launch */
  fun launchLatest(
    key: Any = Unit,
    block: suspend CoroutineScope.() -> Unit,
  ) {
    jobs[key]?.cancel()
    jobs[key] = scope.launch(block = block)
  }
}

/**
 * 典型用途：监听 viewModel 的 event channel 并做出动作
 *
 * 封装的意义：
 * 1. UI 监听 viewmodel event channel 的最佳实践 的样板代码封装
 * 2. 直接提供一个 EventScope 运行事件异步、异步且取消之前action 的能力
 *
 * @param flow: 一般是 viewModel.uiEvent
 * @param key1: 一般写 viewModel
 * @param onEvent: 事件调用；注意它包裹在 `EventScope` 下（本身是一个 CoroutineScope），所以你可以：
 *     1. 直接调用事件，如果它将持续很久，会阻塞后续事件消费
 *     2. 用 [CoroutineScope.launch]: 异步调用，不阻塞事件消费；但是该调用不能控制并发规模
 *     3. 用专门的函数 [EventScope.launchLatest]: 异步调用，且取消之前尚未运行结束的同 key 的任务
 */
@Composable
fun <T> ObserveAsEvents(
    flow: Flow<T>,
    key1: Any? = null,
    onEvent: suspend EventScope.(T) -> Unit,
) {
  //
  val lifecycleOwner = LocalLifecycleOwner.current
  val currentOnEvent by rememberUpdatedState(onEvent)

  // 最佳实践 1：key 里放入 viewModel, lifecycle
  // 相比 LaunchedEffect(flow):
  // 如果以后页面支持平板分栏（比如左边话题列表、右边聊天页，切换话题时 ChatScreen 没销毁，只是换了另一个 viewModel 实例），LaunchedEffect(Unit)
  // 不会重启，它会继续死盯着旧 viewModel 的流。
  // 把它依赖的 viewModel 和 lifecycleOwner 放进 key 里，实例一换就会自动取消旧订阅、重连新实例，属于更严谨的 Compose 习惯。
  LaunchedEffect(flow, lifecycleOwner.lifecycle, key1) {
    // 最佳实践 2：使用 lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED)
    // 核心原因：LaunchedEffect 只感知“组件进出视图树”，不感知“App 退到后台”。
    // 当用户按下 Home 键或切到别的 App（进入 onStop）时，ChatScreen 依然留在 Compose 树里，LaunchedEffect(Unit)
    // 并不会暂停，仍在后台照常消费 Channel 里的事件。这会导致两个问题：
    // - 提示白弹了：如果在后台直接把 Snackbar 弹出来并倒计时消失，用户切回来时啥也没看见；
    // - 后台拉起页面/文件被拦截：像触发系统 Intent 的事件，在后台触发可能被系统限制或直接失效。
    // 加上 repeatOnLifecycle(STARTED) 配合 Channel(BUFFERED) 后：
    // - App 切到后台 -> 暂停收集，事件先安静排队在 Channel 缓冲区里；
    // - 用户切回前台 -> 恢复收集，Snackbar 当面弹出来。
    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
      // 最佳实践 3： withContext(Dispatchers.Main.immediate)（防 Channel 丢事件）
      // 这个比较偏底层，是 Jetpack Compose 社区针对 Channel 踩出来的经典细节：
      // - LaunchedEffect 默认跑在 Compose 的 AndroidUiDispatcher（走 Handler / Choreographer 排队），不是
      // immediate。
      // - 在 Channel 已经把元素从队列里取出来、准备交给 collect { ... } 代码块执行之间，如果发生了一次 Handler.post
      // 调度排队，而恰好在这一瞬间页面退后台触发 repeatOnLifecycle 取消，取出来的那个事件就丢了（没执行，也没放回队列）。
      // - 套一层 Dispatchers.Main.immediate 可以让已经处于主线程的恢复/回调立刻同步执行，不再往消息队列尾部抛一次，极大降低这个并发窗口丢事件的概率。
      withContext(Dispatchers.Main.immediate) {
        val eventScope = EventScope(this)
        flow.collect { event ->
          eventScope.currentOnEvent(event)
        }
      }
    }
  }
}
