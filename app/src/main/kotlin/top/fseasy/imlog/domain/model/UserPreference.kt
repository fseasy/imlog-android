package top.fseasy.imlog.domain.model

data class UserPreference(
    val userId: UserId,
    val mediaStorageRootUri: UriStr?,
    val themeMode: String?,
)
