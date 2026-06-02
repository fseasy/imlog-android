package top.fseasy.imlog.domain.model;

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@JvmInline
value class TopicId(val value: String) {
    init {
        require(value.startsWith(PREFIX)) { "Invalid TopicId prefix"}
    }

    companion object {
        private const val PREFIX = "top_"

        @OptIn(ExperimentalUuidApi::class)
        fun random(): TopicId {
            val uuid = Uuid.generateV7().toHexString()
            return TopicId("${PREFIX}${uuid}")
        }
    }
}

data class Topic(
    val id: TopicId,
    val name: String,
    val iconUri: String?,
    val creatorId: String,
    val createdAt: Long,
    val attributesUpdatedAt: Long,
    val isDeleted: Boolean = false,
)

data class LogScreenTopic(
    val id: TopicId,
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
    val topicId: TopicId,
    val userId: UserId,
    val userNickname: String?,
    val role: String,
    val joinedAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean,
)

data class TopicPersonalState(
    val topicId: TopicId,
    val userId: UserId,
    val isArchived: Boolean = false,
    val isPinned: Boolean = false,
    val background: String? = null,
    val lastReadAt: Long = System.currentTimeMillis(),
    val attributesUpdatedAt: Long = lastReadAt,
)