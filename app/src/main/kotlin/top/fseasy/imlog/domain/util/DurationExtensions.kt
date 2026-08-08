package top.fseasy.imlog.domain.util

fun Int.secondsToMmSsFormat(): String = toLong().secondsToMmSsFormat()

fun Long.secondsToMmSsFormat(): String {
  val mins = this / 60
  val secs = this % 60
  return "%d:%02d".format(mins, secs)
}

fun kotlin.time.Duration.toMmSsFormat(): String = this.inWholeSeconds.secondsToMmSsFormat()

/**
 * Examples:
 * - 0:02
 * - 1:03, 10:22
 * - 1:02:12, 10:11:02
 */
fun Long.secondsToAppMessageTimeFormat(): String {
  return when {
    this < 0 -> "0:00"
    this < 60 -> "0:%02d".format(this)
    this < 3600 -> {
      val mins = this / 60
      val secs = this % 60
      "%d:%02d".format(mins, secs)
    }
    else -> {
      val hours = this / 3600
      val mins = (this % 3600) / 60
      val secs = this % 60
      "%d:%02d:%02d".format(hours, mins, secs)
    }
  }
}

fun kotlin.time.Duration.toAppMessageTimeFormat() =
    this.inWholeSeconds.secondsToAppMessageTimeFormat()

fun kotlin.time.Duration?.safeDivision(duration: kotlin.time.Duration): Float {
  return if (this == null || duration == kotlin.time.Duration.ZERO) {
    0f
  } else {
    (this / duration).toFloat()
  }
}
