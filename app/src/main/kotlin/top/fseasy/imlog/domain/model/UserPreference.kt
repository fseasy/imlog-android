package top.fseasy.imlog.domain.model

import androidx.compose.runtime.Immutable
import android.net.Uri

@Immutable
data class UserPreference(
    val userId: UserId,
    val mediaStorageRootUri: Uri?,
    val themeMode: String,
)