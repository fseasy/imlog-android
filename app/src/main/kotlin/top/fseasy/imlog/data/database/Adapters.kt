package top.fseasy.imlog.data.database

import app.cash.sqldelight.ColumnAdapter
import top.fseasy.imlog.domain.model.AvatarModel
import top.fseasy.imlog.domain.model.MessageDraft
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.TopicMemberRole
import top.fseasy.imlog.domain.model.TopicPresetAvatar
import top.fseasy.imlog.domain.model.UriStr
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.model.UserPresetAvatar
import top.fseasy.imlog.domain.model.serialize
import top.fseasy.imlog.domain.util.defaultJson
import top.fseasy.imlog.ui.model.toAvatarModelOrNull

val userIdAdapter =
    object : ColumnAdapter<UserId, String> {
      override fun decode(databaseValue: String): UserId = UserId(databaseValue)

      override fun encode(value: UserId): String = value.value
    }

val userAvatarModelAdapter =
    object : ColumnAdapter<AvatarModel<UserPresetAvatar>, String> {
      override fun decode(databaseValue: String): AvatarModel<UserPresetAvatar> =
          databaseValue.toAvatarModelOrNull() ?: AvatarModel.Preset.default()

      override fun encode(value: AvatarModel<UserPresetAvatar>): String = value.serialize()
    }

val topicIdAdapter =
    object : ColumnAdapter<TopicId, String> {
      override fun decode(databaseValue: String): TopicId = TopicId(databaseValue)

      override fun encode(value: TopicId): String = value.value
    }

val topicAvatarModelAdapter =
    object : ColumnAdapter<AvatarModel<TopicPresetAvatar>, String> {
      override fun decode(databaseValue: String): AvatarModel<TopicPresetAvatar> =
          databaseValue.toAvatarModelOrNull() ?: AvatarModel.Preset.default()

      override fun encode(value: AvatarModel<TopicPresetAvatar>): String = value.serialize()
    }

val topicMemberRoleAdapter =
    object : ColumnAdapter<TopicMemberRole, String> {
      override fun decode(databaseValue: String) =
          TopicMemberRole.fromValue(databaseValue) ?: TopicMemberRole.default

      override fun encode(value: TopicMemberRole) = value.value
    }

val messageIdAdapter =
    object : ColumnAdapter<MessageId, String> {
      override fun decode(databaseValue: String): MessageId = MessageId(databaseValue)

      override fun encode(value: MessageId): String = value.value
    }

val messageTypeAdapter =
    object : ColumnAdapter<MessageType, String> {
      override fun decode(databaseValue: String): MessageType =
          MessageType.fromValueOrDefault(databaseValue)

      override fun encode(value: MessageType): String = value.name
    }

val messageDraftAdapter =
    object : ColumnAdapter<Result<MessageDraft>, String> {

      override fun decode(databaseValue: String) = runCatching {
        defaultJson.decodeFromString<MessageDraft>(databaseValue)
      }

      override fun encode(value: Result<MessageDraft>): String =
          value.fold(
              onSuccess = { defaultJson.encodeToString(it) },
              onFailure = { error("Encode must pass the actual value!") },
          )
    }

val uriStrAdapter =
    object : ColumnAdapter<UriStr, String> {
      override fun decode(databaseValue: String): UriStr = UriStr(databaseValue)

      override fun encode(value: UriStr): String = value.value
    }


val intAdapter = object : ColumnAdapter<Int, Long> {
  override fun decode(databaseValue: Long): Int = databaseValue.toInt()
  override fun encode(value: Int): Long = value.toLong()
}