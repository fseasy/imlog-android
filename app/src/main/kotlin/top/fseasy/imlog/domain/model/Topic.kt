package top.fseasy.imlog.domain.model;

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
