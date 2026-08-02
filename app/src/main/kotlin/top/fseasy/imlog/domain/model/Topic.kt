package top.fseasy.imlog.domain.model;

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.jvm.JvmInline

@JvmInline
@Serializable
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

data class Topic(
    val id: TopicId,
    val name: String,
    val avatarModel: TopicAvatarModel,
    val description: String?,
    val creatorId: UserId?, // Can be null if creator is deleted
    val createdAt: Long,
    val attributesUpdatedAt: Long,
)

/**
 * Element to show in Home screen topic lists.
 */
data class HomeTopic(
    val id: TopicId,
    val name: String,
    val avatarModel: TopicAvatarModel,
    val isPinned: Boolean,
    val hasUnread: Boolean,
    val messageUpdatedAt: Long,
    val latestMessagePreview: MessagePreview?,
    val draft: MessageDraft?,
    val description: String?,
)

enum class TopicMemberRole(val value: String) {
    Admin("admin"), Logger("logger"), Watcher("watcher");

    companion object {
        private val valueMap = entries.associateBy { it.value }
        fun fromValue(value: String): TopicMemberRole? = valueMap[value]

        /**
         * Default is local logging => admin
         */
        val default: TopicMemberRole
            get() = TopicMemberRole.Admin
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

data class TopicPreference(
    val topicId: TopicId,
    val userId: UserId,
    val isArchived: Boolean = false,
    val isPinned: Boolean = false,
    val background: String? = null,
)
