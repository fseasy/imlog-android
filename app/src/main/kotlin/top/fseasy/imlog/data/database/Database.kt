package top.fseasy.imlog.data.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import top.fseasy.imlog.sqldelight.SqlDelightDb
import top.fseasy.imlog.sqldelight.App_init_data.Adapter as AppInitDataAdapter
import top.fseasy.imlog.sqldelight.Message_attachment_processing_task_states.Adapter as AttachmentProcessingTaskStatesAdapter
import top.fseasy.imlog.sqldelight.Messages.Adapter as MessageAdapter
import top.fseasy.imlog.sqldelight.Topic_members.Adapter as TopicMembersAdapter
import top.fseasy.imlog.sqldelight.Topic_message_state.Adapter as TopicMessageStateAdapter
import top.fseasy.imlog.sqldelight.Topic_preference.Adapter as TopicPreferenceAdapter
import top.fseasy.imlog.sqldelight.Topics.Adapter as TopicsAdapter
import top.fseasy.imlog.sqldelight.User_preference.Adapter as UserPreferenceAdapter
import top.fseasy.imlog.sqldelight.Users.Adapter as UsersAdapter

/** It will be provided as singleton by Hilt Singleton Binds. see `di.DatabseModel` */
fun createSqlDelightDb(context: Context): SqlDelightDb {
  val driver =
      AndroidSqliteDriver(
          schema = SqlDelightDb.Schema,
          context = context,
          name = "app.db",
          // Enable foreign_keys to enable cascade delete
          callback =
              object : AndroidSqliteDriver.Callback(SqlDelightDb.Schema) {
                override fun onOpen(db: SupportSQLiteDatabase) {
                  super.onOpen(db)
                  // Enable foreign keys
                  db.setForeignKeyConstraintsEnabled(true)
                }
              },
      )
  return SqlDelightDb(
      driver = driver,
      messagesAdapter =
          MessageAdapter(
              idAdapter = messageIdAdapter,
              topic_idAdapter = topicIdAdapter,
              sender_idAdapter = userIdAdapter,
              typeAdapter = messageTypeAdapter,
              quoted_message_idAdapter = messageIdAdapter,
              widthAdapter = intAdapter,
              heightAdapter = intAdapter,
          ),
      topic_message_stateAdapter =
          TopicMessageStateAdapter(
              draftAdapter = messageDraftAdapter,
              topic_idAdapter = topicIdAdapter,
              user_idAdapter = userIdAdapter,
          ),
      topic_membersAdapter =
          TopicMembersAdapter(
              topic_idAdapter = topicIdAdapter,
              user_idAdapter = userIdAdapter,
              roleAdapter = topicMemberRoleAdapter,
          ),
      topic_preferenceAdapter =
          TopicPreferenceAdapter(
              topic_idAdapter = topicIdAdapter,
              user_idAdapter = userIdAdapter,
          ),
      topicsAdapter =
          TopicsAdapter(
              idAdapter = topicIdAdapter,
              creator_idAdapter = userIdAdapter,
              avatar_modelAdapter = topicAvatarModelAdapter,
          ),
      app_init_dataAdapter = AppInitDataAdapter(user_idAdapter = userIdAdapter),
      message_attachment_processing_task_statesAdapter =
          AttachmentProcessingTaskStatesAdapter(
              message_idAdapter = messageIdAdapter,
              src_uriAdapter = uriStrAdapter,
          ),
      user_preferenceAdapter =
          UserPreferenceAdapter(
              user_idAdapter = userIdAdapter,
              shared_storage_root_uriAdapter = uriStrAdapter,
          ),
      usersAdapter =
          UsersAdapter(idAdapter = userIdAdapter, avatar_modelAdapter = userAvatarModelAdapter),
  )
}
