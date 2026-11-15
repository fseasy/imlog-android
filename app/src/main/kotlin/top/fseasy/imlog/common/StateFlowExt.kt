package top.fseasy.imlog.common

import kotlinx.coroutines.flow.MutableStateFlow

sealed interface TransitionDecision<out T> {
  data object Skip : TransitionDecision<Nothing>

  data class Next<out T>(val state: T) : TransitionDecision<T>
}

sealed interface TransitionResult<out T> {
  data object Skipped : TransitionResult<Nothing>

  data class Done<out T>(val previous: T, val current: T) : TransitionResult<T>
}

/**
 * 原子地尝试将 [MutableStateFlow] 从当前状态迁移到下一个状态。
 *
 * 注意：并发竞争时 [decide] 可能会被重试执行多次，请确保其为无副作用的纯函数，且不要抛出任何异常！
 */
inline fun <T> MutableStateFlow<T>.tryTransition(
    decide: (current: T) -> TransitionDecision<T>
): TransitionResult<T> {
  while (true) {
    val current = value
    when (val decision = decide(current)) {
      TransitionDecision.Skip -> return TransitionResult.Skipped
      is TransitionDecision.Next -> {
        if (compareAndSet(current, decision.state)) {
          return TransitionResult.Done(previous = current, current = decision.state)
        }
      }
    }
  }
}
