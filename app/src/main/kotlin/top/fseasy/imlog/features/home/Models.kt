package top.fseasy.imlog.features.home


sealed interface ResourceModel {
    data class FromUri(val uri: android.net.Uri) : ResourceModel
    data class FromFile(val file: java.io.File) : ResourceModel
}