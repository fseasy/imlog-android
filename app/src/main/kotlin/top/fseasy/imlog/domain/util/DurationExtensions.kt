package top.fseasy.imlog.domain.util

fun Int.secondsToMmSsFormat(): String = toLong().secondsToMmSsFormat()

fun Long.secondsToMmSsFormat(): String {
    val mins = this / 60
    val secs = this % 60
    return "%d:%02d".format(mins, secs)
}

fun kotlin.time.Duration.toMmSsFormat(): String = this.inWholeSeconds.secondsToMmSsFormat()