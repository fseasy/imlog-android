package top.fseasy.imlog.ui.util

fun Long.byteSizeToHumanReadable(): String {
  if (this < 0) return "0 B"
  if (this < 1024) return "$this B"

  val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB", "EB")
  var size = this.toDouble()
  var unitIndex = 0

  while (size >= 1024 && unitIndex < units.lastIndex) {
    size /= 1024
    unitIndex++
  }

  return when {
    size >= 100 -> "%.0f %s".format(size, units[unitIndex]) // 100+ -> don't display dot
    size >= 10 -> "%.1f %s".format(size, units[unitIndex]) // 10~99 -> keep 1 precision
    else -> "%.2f %s".format(size, units[unitIndex]) // <10 -> keep 2 precision
  }
}
