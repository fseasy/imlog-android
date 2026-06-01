package top.fseasy.imlog.domain.model;


data class Topic(
    val id: String = "__EMPTY_ID__",
    val name: String = "__EMPTY_NAME__",
    val iconUri: String? = null,
    val creatorId: String = "__EMPTY_CREATOR_ID__",
    val createdAt: Long = 0L,
    val attributesUpdatedAt: Long = 0L,
    val isDeleted: Boolean = false,
) {
    companion object {
        /**
         * Use this if you need an empty object
         */
        val EMPTY = Topic()
    }
}

data class LogScreenTopic(
    val id: String,
    val name: String,
    val iconUri: String?,
    val isPinned: Boolean,
    val hasUnread: Boolean,
    val messageUpdatedAt: Long,
    val lastMessageSnippet: String?,
    val background: String? = null,
)

enum class TopicRole(val value: String) {
    ADMIN("admin"),
    LOGGER("logger"),
    WATCHER("watcher");

    companion object {
        private val valueMap = entries.associateBy { it.value }
        fun fromValue(value: String): TopicRole? = valueMap[value]
    }
}

data class TopicMember(
    val topicId: String,
    val userId: String,
    val userNickname: String?,
    val role: String,
    val joinedAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean,
)

data class TopicPersonalState(
    val topicId: String,
    val userId: String,
    val isArchived: Boolean = false,
    val isPinned: Boolean = false,
    val background: String? = null,
    val lastReadAt: Long = System.currentTimeMillis(),
    val attributesUpdatedAt: Long = lastReadAt,
) {
    companion object {
        fun default(topicId: String, userId: String): TopicPersonalState =
            TopicPersonalState(topicId = topicId, userId = userId)
    }
}
