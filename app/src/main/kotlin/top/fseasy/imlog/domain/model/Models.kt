package top.fseasy.imlog.domain.model

enum class MessageType {
    TEXT, IMAGE, VIDEO, AUDIO, FILE
}

data class Message(
    val id: String,
    val topicId: String,
    val senderId: String,
    val type: MessageType,
    val content: String? = null,
    val filePath: String? = null,
    val fileSize: Long? = 0,
    val duration: Long? = 0,
    val thumbnailPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long? = null,
    val isDeleted: Boolean = false
)

data class Statistics(
    val totalDays: Int,
    val totalMessages: Int
)
