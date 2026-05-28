package top.fseasy.imtrace.app.domain.model

data class User(
    val id: String,
    val username: String,
    val avatarUri: String?,
    val createdAt: Long,
    val updatedAt: Long
)

data class Topic(
    val id: String,
    val name: String,
    val iconUri: String?,
    val creatorId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val background: String? = null,
    val font: String? = null
)

data class TopicMember(
    val topicId: String,
    val userId: String,
    val userNickname: String?,
    val role: String,
    val joinedAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean
)

enum class MessageType {
    TEXT, IMAGE, VIDEO, AUDIO, FILE
}

data class Message(
    val id: String,
    val topicId: String,
    val senderId: String,
    val type: MessageType,
    val content: String?,
    val filePath: String?,
    val fileSize: Long?,
    val duration: Long?,
    val thumbnailPath: String?,
    val createdAt: Long,
    val updatedAt: Long?,
    val isDeleted: Boolean
)

data class Statistics(
    val totalDays: Int,
    val totalMessages: Int
)
