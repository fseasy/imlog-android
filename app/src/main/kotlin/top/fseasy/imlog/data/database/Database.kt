package top.fseasy.imlog.data.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import top.fseasy.imlog.sqldelight.SqlDelightDb
import top.fseasy.imlog.sqldelight.Messages.Adapter as MessageAdapter
import top.fseasy.imlog.sqldelight.Topic_members.Adapter as TopicMembersAdapter
import top.fseasy.imlog.sqldelight.Topic_message_state.Adapter as TopicMessageStateAdapter
import top.fseasy.imlog.sqldelight.Topic_personal_preference.Adapter as TopicPersonalPreferenceAdapter
import top.fseasy.imlog.sqldelight.Topics.Adapter as TopicsAdapter


/**
 * It will be provided as singleton by Hilt Singleton Binds. see `di.DatabseModel`
 */
fun createSqlDelightDb(context: Context): SqlDelightDb {
    val driver = AndroidSqliteDriver(
        schema = SqlDelightDb.Schema,
        context = context,
        name = "app.db",
        // Enable foreign_keys to enable cascade delete
        callback = object : AndroidSqliteDriver.Callback(SqlDelightDb.Schema) {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                // Enable foreign keys
                db.setForeignKeyConstraintsEnabled(true)
            }
        }
    )
    return SqlDelightDb(
        driver = driver,
        messagesAdapter = MessageAdapter(
            reply_to_messageAdapter = quoteMessageAdapter,
        ),
        topic_message_stateAdapter = TopicMessageStateAdapter(
            latest_message_previewAdapter = messagePreviewAdapter,
            draftAdapter = messageDraftAdapter,
            topic_idAdapter = topicIdAdapter,
            user_idAdapter = userIdAdapter,
        ),
        topic_membersAdapter = TopicMembersAdapter(
            topic_idAdapter = topicIdAdapter,
            user_idAdapter = userIdAdapter,
            roleAdapter = topicMemberRoleAdapter
        ),
        topic_personal_preferenceAdapter = TopicPersonalPreferenceAdapter(
            topic_idAdapter = topicIdAdapter,
            user_idAdapter = userIdAdapter
        ),
        topicsAdapter = TopicsAdapter(
            idAdapter = topicIdAdapter,
            creator_idAdapter = userIdAdapter
        ),
    )
}
