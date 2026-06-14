package top.fseasy.imlog.domain.model;

import androidx.compose.runtime.Immutable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@JvmInline
value class TopicId(val value: String) {
    init {
        require(value.startsWith(PREFIX)) { "Invalid TopicId prefix" }
    }

    companion object {
        private const val PREFIX = "top_"

        @OptIn(ExperimentalUuidApi::class)
        fun random(): TopicId {
            val uuid = Uuid.generateV7()
                .toHexString()
            return TopicId("${PREFIX}${uuid}")
        }
    }
}

@Immutable
data class Topic(
    val id: TopicId,
    val name: String,
    val iconUri: String?,
    val creatorId: UserId?, // Can be null if creator is deleted
    val createdAt: Long,
    val attributesUpdatedAt: Long,
    val isDeleted: Boolean = false,
)

@Immutable
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
    ADMIN("admin"), LOGGER("logger"), WATCHER("watcher");

    companion object {
        private val valueMap = entries.associateBy { it.value }
        fun fromValue(value: String): TopicRole? = valueMap[value]
    }
}

@Immutable
data class TopicMember(
    val topicId: TopicId,
    val userId: UserId,
    val userNickname: String?,
    val role: String,
    val joinedAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean,
)

@Immutable
data class TopicPersonalState(
    val topicId: TopicId,
    val userId: UserId,
    val isArchived: Boolean = false,
    val isPinned: Boolean = false,
    val background: String? = null,
    val lastReadAt: Long = System.currentTimeMillis(),
    val attributesUpdatedAt: Long = lastReadAt,
)

/**
 * A data to represent the join query result of Topic + TopicPersonalState.
 * nb: no default values as it should be init from the entity directly.
 */
@Immutable
data class TopicWithPersonalState(
    val topic: Topic,
    val isArchived: Boolean,
    val isPinned: Boolean,
    val background: String?,
    val lastReadAt: Long,
)