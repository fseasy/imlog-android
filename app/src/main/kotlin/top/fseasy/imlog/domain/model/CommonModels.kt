package top.fseasy.imlog.domain.model

import kotlinx.serialization.Serializable

data class Statistics(
    val totalDays: Long,
    val totalMessages: Long,
)

/**
 * The uri representation in Domain level, without introducing the android platform dependency
 */
@JvmInline
@Serializable
value class UriStr(val value: String) {
    init {
        require(value.isNotBlank()) { "URI string cannot be blank" }
    }
}
