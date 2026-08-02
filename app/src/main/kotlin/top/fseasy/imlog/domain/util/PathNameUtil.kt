package top.fseasy.imlog.domain.util

/**
 * Extract path name by substring-after-last.
 * not stable enough for dirty paths. Use it only when you know the path is very clean.
 */
fun String?.pathNameBySubstring(): String? {
    return this?.let { substringAfterLast("/") }
}

fun String.splitNameAndExtension(): Pair<String, String> {
    val lastDotIndex = lastIndexOf(".")
    return if (lastDotIndex == -1) {
        this to ""
    } else {
        substring(0, lastDotIndex) to substring(lastDotIndex + 1)
    }
}
