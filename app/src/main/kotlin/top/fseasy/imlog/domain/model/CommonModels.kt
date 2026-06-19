package top.fseasy.imlog.domain.model

data class Statistics(
    val totalDays: Long,
    val totalMessages: Long,
)

/**
 * The uri representation in Domain level, without introducing the android platform dependency
 */
@JvmInline
value class UriStr(val value: String) {
    init {
        require(value.isNotBlank()) { "URI string cannot be blank" }
    }
}

