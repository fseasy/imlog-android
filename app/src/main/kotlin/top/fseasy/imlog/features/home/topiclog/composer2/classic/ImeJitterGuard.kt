package top.fseasy.imlog.features.home.topiclog.composer2.classic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import timber.log.Timber

const val RACE_CONDITION_DEBOUNCE_WINDOW_SECONDS = 1.5

/**
 * 输入法打断竞态保护器.
 *
 * 背景： 软键盘在未完全收起前，又被调起弹出，导致弹出、收起动画存在竞态. 这时系统给出的 target height 是不准确的（包含了一些不可见元素）
 * 在一段时间后，会恢复到实际值；在这个改变之间，存在高度的跳变；
 *
 * 这个 Guard 非常简单，记录上次 focus 取消的时刻，判断此次弹出是否和上次时间间隔太近——太近则认为有竞态
 *
 * @param debounceWindow the internal to unblock the race-condition. Based on the Log, for emulator
 *   app, the duration from unfocus to fully closed is ~1.4s
 */
@Stable
internal class ImeJitterGuard(
    private val debounceWindow: Duration = RACE_CONDITION_DEBOUNCE_WINDOW_SECONDS.seconds,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
  // 记录上一次的焦点状态，初始为 false
  private var wasFocused = false

  // 记录最后一次真正失焦的时间标记（TimeMark）
  private var lastUnfocusMark by mutableStateOf<TimeMark?>(null)

  /** 当输入框焦点状态变化时调用 */
  fun onFocusChanged(focused: Boolean) {
    Timber.i("Called Focus Changed! focus = $focused, wasFocused = $wasFocused")

    // 关键修复：
    // 只有在上一次确实是 focused (true)，而当前变成了 not focused (false) 时，
    // 才是一次真正的“键盘收起/失焦”事件！
    // 页面初次进入时触发的 focused = false 会因为 wasFocused == false 被完美过滤掉。
    if (wasFocused && !focused) {
      lastUnfocusMark = timeSource.markNow()
    }

    // 更新上一次的焦点状态
    wasFocused = focused
  }

  /** 是否处于打断的高危时间窗口（距离上次失焦尚未超过设定阈值） */
  val isInRaceConditionWindow: Boolean
    get() {
      val mark = lastUnfocusMark ?: return false
      return mark.elapsedNow() < debounceWindow
    }

  /** 过滤系统 Insets，防止打断期间键盘高度突跳冒顶 */
  fun sanitizeImeHeight(currentImePx: Int, cachedHeightPx: Int): Int {
    return if (isInRaceConditionWindow && cachedHeightPx > 0) {
      minOf(currentImePx, cachedHeightPx)
    } else {
      currentImePx
    }
  }

  /** 判定当前采样到的 Target 是否允许更新到持久缓存 */
  fun canUpdateCache(): Boolean = !isInRaceConditionWindow
}

@Composable
internal fun rememberImeJitterGuard(
    debounceWindow: Duration = RACE_CONDITION_DEBOUNCE_WINDOW_SECONDS.seconds
): ImeJitterGuard {
  return remember(debounceWindow) { ImeJitterGuard(debounceWindow) }
}
