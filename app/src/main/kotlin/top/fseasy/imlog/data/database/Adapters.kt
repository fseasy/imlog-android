package top.fseasy.imlog.data.database

import app.cash.sqldelight.ColumnAdapter
import top.fseasy.imlog.domain.model.MessageDraft
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.MessagePreview
import top.fseasy.imlog.domain.model.QuoteMessage
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.TopicMemberRole
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.util.defaultJson


val userIdAdapter = object : ColumnAdapter<UserId, String> {
    override fun decode(databaseValue: String): UserId = UserId(databaseValue)

    override fun encode(value: UserId): String = value.value
}

val topicIdAdapter = object : ColumnAdapter<TopicId, String> {
    override fun decode(databaseValue: String): TopicId = TopicId(databaseValue)

    override fun encode(value: TopicId): String = value.value
}

val messageIdAdapter = object : ColumnAdapter<MessageId, String> {
    override fun decode(databaseValue: String): MessageId = MessageId(databaseValue)

    override fun encode(value: MessageId): String = value.value
}

val topicMemberRoleAdapter = object : ColumnAdapter<TopicMemberRole, String> {
    override fun decode(databaseValue: String) =
        TopicMemberRole.fromValue(databaseValue) ?: TopicMemberRole.default

    override fun encode(value: TopicMemberRole) = value.value

}

val quoteMessageAdapter = object : ColumnAdapter<Result<QuoteMessage>, String> {
    override fun decode(databaseValue: String) = runCatching {
        defaultJson.decodeFromString<QuoteMessage>(databaseValue)
    }

    override fun encode(value: Result<QuoteMessage>): String = value.fold(
        onSuccess = { defaultJson.encodeToString(it) },
        onFailure = { error("Encode must pass the actual value!") }
    )
}

val messagePreviewAdapter = object : ColumnAdapter<Result<MessagePreview>, String> {

    override fun decode(databaseValue: String) = runCatching {
        defaultJson.decodeFromString<MessagePreview>(databaseValue)
    }

    override fun encode(value: Result<MessagePreview>): String = value.fold(
        onSuccess = { defaultJson.encodeToString(it) },
        onFailure = { error("Encode must pass the actual value!") }
    )
}

val messageDraftAdapter = object : ColumnAdapter<Result<MessageDraft>, String> {

    override fun decode(databaseValue: String) = runCatching {
        defaultJson.decodeFromString<MessageDraft>(databaseValue)
    }

    override fun encode(value: Result<MessageDraft>): String = value.fold(
        onSuccess = { defaultJson.encodeToString(it) },
        onFailure = { error("Encode must pass the actual value!") }
    )
}