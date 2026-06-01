package top.fseasy.imlog.domain.model

data class User(
    val id: String,
    val username: String,
    val avatarUri: String?,
    val createdAt: Long,
    val updatedAt: Long
)

