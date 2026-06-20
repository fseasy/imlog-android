package top.fseasy.imlog.domain.util

fun Int.secondsToMinutesSeconds(): String = toLong().secondsToMinutesSeconds()

fun Long.secondsToMinutesSeconds(): String {
    val mins = this / 60
    val secs = this % 60
    return "%d:%02d".format(mins, secs)
}