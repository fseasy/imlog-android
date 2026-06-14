package top.fseasy.imlog.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class Statistics(
    val totalDays: Long,
    val totalMessages: Long,
)

sealed interface ResourceModel {
    data class Uri(val uri: android.net.Uri) : ResourceModel
    data class File(val file: java.io.File) : ResourceModel
}