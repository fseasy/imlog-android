package top.fseasy.imlog.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import top.fseasy.imlog.domain.model.LogScreenTopic
import top.fseasy.imlog.domain.model.Topic
import top.fseasy.imlog.domain.model.TopicRole
import top.fseasy.imlog.domain.repository.TopicRepository
import top.fseasy.imlog.sqldelight.SqlDelightDb
import top.fseasy.imlog.util.retrySQLiteOnKeyConflict
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import timber.log.Timber
import top.fseasy.imlog.domain.model.TopicPersonalState

@Singleton
class TopicRepositoryImpl @Inject constructor(
    private val database: SqlDelightDb,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TopicRepository {

    override fun observeTopicById(topicId: String): Flow<Topic> =
        database.topicQueries
            .getTopicById(topicId)
            .asFlow()
            .mapToOne(dispatcher)
            .map { row ->
                Topic(
                    id = row.id,
                    name = row.name,
                    iconUri = row.icon_uri,
                    creatorId = row.creator_id,
                    createdAt = row.created_at,
                    attributesUpdatedAt = row.attributes_updated_at,
                    isDeleted = row.is_deleted == 1L,
                )
            }
            .catch { e ->
                Timber.w(e, "No Topic found for id=${topicId}, emit empty")
                emit(Topic.EMPTY)
            }

    override fun observePersonalStateById(
        userId: String,
        topicId: String,
    ): Flow<TopicPersonalState> =
        database.topicQueries
            .getPersonalState(topic_id = topicId, user_id = userId)
            .asFlow()
            .mapToOne(dispatcher)
            .map { row ->
                TopicPersonalState(
                    topicId = topicId,
                    userId = userId,
                    isArchived = row.archived == 1L,
                    isPinned = row.pinned == 1L,
                    background = row.background,
                    lastReadAt = row.last_read_at ?: System.currentTimeMillis(),
                    attributesUpdatedAt = row.attributes_updated_at
                )
            }
            .catch { e ->
                Timber.i(e, "No TopicPersonalState found for id=${topicId}, emit default")
                emit(TopicPersonalState.default(topicId=topicId, userId=userId))
            }

    /**
     * Used for Log Screen Topics lists (home screen)
     */
    override fun observeLogScreenTopics(userId: String): Flow<List<LogScreenTopic>> {
        return database.topicQueries
            .getCurrentUserLogScreenTopics(userId)
            .asFlow()
            .mapToList(dispatcher)
            .map { rows ->
                rows.map { row ->
                    LogScreenTopic(
                        id = row.id,
                        name = row.name,
                        iconUri = row.icon_uri,
                        isPinned = row.pinned == 1L,
                        hasUnread = row.has_unread == 1L,
                        messageUpdatedAt = row.topic_message_update_at,
                        lastMessageSnippet = row.last_message_snippet,
                        background = row.background
                    )
                }
            }
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun createTopic(
        creatorId: String, name: String, iconUri: String?,
    ): LogScreenTopic {
        val now = System.currentTimeMillis()
        val topicId = retrySQLiteOnKeyConflict {
            Uuid.generateV7().toHexString().also { newId ->
                database.transaction {
                    // needs to insert to 3 tables: 1. topic 2. personal state 3. topic-members
                    database.topicQueries.insertTopic(
                        id = newId,
                        name = name,
                        icon_uri = iconUri,
                        creator_id = creatorId,
                        created_at = now,
                        attributes_updated_at = now
                    )
                    database.topicQueries.insertPersonalState(
                        topic_id = newId,
                        user_id = creatorId,
                        archived = 0L,
                        pinned = 0L,
                        background = null,
                        last_read_at = now, // set to now when init
                        attributes_updated_at = now
                    )
                    database.topicQueries.insertMember(
                        topic_id = newId,
                        user_id = creatorId,
                        user_nickname = null, // use null so it can adapt to the latest name
                        role = TopicRole.ADMIN.value,
                        joined_at = now,
                        attributes_updated_at = now
                    )
                }
            }
        }

        return LogScreenTopic(
            id = topicId,
            name = name,
            iconUri = iconUri,
            isPinned = false,
            hasUnread = false,
            messageUpdatedAt = now,
            lastMessageSnippet = null,
            background = null
        )
    }

    override suspend fun updateTopicName(
        userId: String,
        topicId: String,
        newName: String,
    ): Boolean {
        val now = System.currentTimeMillis()
        val rowsAffected = database.topicUpdateQueries.updateTopicName(
            newName = newName,
            updatedAt = now,
            topicId = topicId,
            userId = userId
        ).value;
        return rowsAffected > 0L;
    }

    override suspend fun updateTopicIcon(
        userId: String,
        topicId: String,
        newIconUri: String,
    ): Boolean {
        val now = System.currentTimeMillis()
        val rowsAffected = database.topicUpdateQueries.updateTopicIconUri(
            newIconUri = newIconUri,
            updatedAt = now,
            topicId = topicId,
            userId = userId
        ).value;
        return rowsAffected > 0L;
    }

    override suspend fun updateTopicBackground(
        userId: String,
        topicId: String,
        background: String?,
    ): Boolean {
        val now = System.currentTimeMillis()
        val rowsAffected = database.topicUpdateQueries.updateTopicPersonalBackground(
            newBackground = background,
            updatedAt = now,
            topicId = topicId,
            userId = userId
        ).value;
        return rowsAffected > 0L;
    }

    override suspend fun pinTopic(userId: String, topicId: String, pinned: Boolean): Boolean {
        val now = System.currentTimeMillis()
        val rowsAffected = database.topicUpdateQueries.updateTopicPersonalPinned(
            newPinned = if (pinned) 1L else 0L,
            updatedAt = now,
            topicId = topicId,
            userId = userId
        ).value;
        return rowsAffected > 0L;
    }

    override suspend fun archiveTopic(userId: String, topicId: String, archived: Boolean): Boolean {
        val now = System.currentTimeMillis()
        val rowsAffected = database.topicUpdateQueries.updateTopicPersonalArchived(
            newArchived = if (archived) 1L else 0L,
            updatedAt = now,
            topicId = topicId,
            userId = userId
        ).value;
        return rowsAffected > 0L;
    }

    override suspend fun deleteTopic(userId: String, topicId: String): Boolean {
        val now = System.currentTimeMillis()
        val rowsAffected = database.topicUpdateQueries.softDeleteTopic(
            updatedAt = now,
            topicId = topicId,
            userId = userId
        ).value;
        return rowsAffected > 0L;
    }
}