package top.fseasy.imlog.domain.model

import android.net.Uri

data class UserPreference(
    val userId: UserId,
    val mediaStorageRootUri: Uri?,
    val themeMode: String,
)