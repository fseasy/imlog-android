package top.fseasy.imlog.features.home.model

import android.net.Uri
import java.io.File


sealed interface ResourceModel {
    data class FromUri(val uri: Uri) : ResourceModel
    data class FromFile(val file: File) : ResourceModel
}

