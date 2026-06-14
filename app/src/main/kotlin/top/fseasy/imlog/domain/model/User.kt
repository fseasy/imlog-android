package top.fseasy.imlog.domain.model

import androidx.compose.runtime.Immutable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Immutable
data class User(
    val id: UserId,
    val username: String,
    val avatarUri: String?,
    val createdAt: Long,
    val attributesUpdatedAt: Long,
)

@Immutable
@JvmInline
value class UserId(val value: String) {
    init {
        require(value.startsWith(PREFIX)) { "Invalid UserId prefix" }
    }

    companion object {
        private const val PREFIX = "usr_"

        @OptIn(ExperimentalUuidApi::class)
        fun random(): UserId {
            val uuid = Uuid.generateV7()
                .toHexString()
            return UserId("${PREFIX}${uuid}")
        }
    }
}