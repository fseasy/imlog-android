package top.fseasy.imlog.ui.util

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch

@Stable
interface SnackbarController {
  /**
   * 注意，这里 show 方法只暴露了 message 字段，把 action 相关的去掉了，是因为 action 的处理逻辑，只能通过回调传入，但回调闭包可能引入 viewModel
   * 的内容，这往往导致异常发生：
   * - 因为若 snackbar 不消失，会导致 viewModel 也不释放 —— 但若 viewModel 已经随页面出栈，那么它的 onCleared 已经被调用过了， 虽然没有被 gc,
   *   但内部状态已经崩坏，触发回调可能导致未知状态。
   *
   * 要想使用 action，请用 [showAndAwaitAction] api.
   */
  fun show(message: String)

  /**
   * 给 [ObserveAsEvents] / LaunchedEffect 使用（跑在调用方自己的作用域，退出自动收起）
   *
   * 返回动作按钮是否被点击的结果, 进而用来执行你的 action. (避免了闭包传入，但是是 suspend)
   */
  suspend fun showAndAwaitAction(
      message: String,
      actionLabel: String? = null,
      withDismissAction: Boolean = actionLabel != null,
      duration: SnackbarDuration =
          if (actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
  ): SnackbarResult

  data object NoOp : SnackbarController {
    override fun show(
        message: String,
    ) = Unit

    override suspend fun showAndAwaitAction(
        message: String,
        actionLabel: String?,
        withDismissAction: Boolean,
        duration: SnackbarDuration,
    ): SnackbarResult = SnackbarResult.Dismissed
  }
}

val LocalSnackbarController =
    staticCompositionLocalOf<SnackbarController> {
      SnackbarController.NoOp // 保证子组件 @Preview 永远不会崩
    }

class DefaultSnackbarController(
    private val hostState: SnackbarHostState,
    private val scope: CoroutineScope,
) : SnackbarController {
  private var currentJob: Job? = null

  override fun show(
      message: String,
  ) {
    currentJob?.cancel()
    hostState.currentSnackbarData?.dismiss()
    currentJob = scope.launch {
      hostState.showSnackbar(
          message = message,
      )
    }
  }

  override suspend fun showAndAwaitAction(
      message: String,
      actionLabel: String?,
      withDismissAction: Boolean,
      duration: SnackbarDuration,
  ): SnackbarResult {
    currentJob?.cancel()
    val callerJob = currentCoroutineContext()[Job]
    currentJob = callerJob
    hostState.currentSnackbarData?.dismiss()
    return try {
      hostState.showSnackbar(
          message = message,
          actionLabel = actionLabel,
          withDismissAction = withDismissAction,
          duration = duration,
      )
    } finally {
      if (currentJob === callerJob) currentJob = null
    }
  }
}
