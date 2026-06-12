package top.fseasy.imlog.domain.model

import androidx.compose.runtime.Immutable

@Immutable
class AppInitData(
    val userId: UserId,
    val mediaStorageRootUriStr: String?, // String is enough

)