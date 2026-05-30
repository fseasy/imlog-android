package top.fseasy.imlog.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import top.fseasy.imlog.data.datastore.AppPreferencesRepository
import top.fseasy.imlog.domain.model.Topic
import top.fseasy.imlog.domain.repository.TopicRepository
import top.fseasy.imlog.sqldelight.SqlDelightDb
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TopicRepositoryImpl @Inject constructor(
    private val database: SqlDelightDb,
    private val appPreferences: AppPreferencesRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TopicRepository {

    override val currentUserId: Flow<String?> = appPreferences.currentUserId

    override fun getTopics(): Flow<List<Topic>> =
        database.topicQueries.getActiveTopics().asFlow().mapToList(dispatcher).map { rows ->
            rows.map { row ->
                val personalState =
                    database.topicQueries
                        .getPersonalState(row.id, currentUserId ?: "")
                        .executeAsList()
                        .firstOrNull()
                Topic(
                    id = row.id,
                    name = row.name,
                    iconUri = row.icon_uri,
                    creatorId = row.creator_id,
                    createdAt = row.created_at,
                    updatedAt = row.updated_at,
                    isDeleted = row.is_deleted,
                    isPinned = personalState?.pinned ?: false,
                    isArchived = personalState?.archived ?: false,
                    background = personalState?.background,
                    font = database.topicQueries.getDeviceState(row.id).executeAsList()
                        .firstOrNull()?.font
                )
            }
        }

    override suspend fun createTopic(name: String, creatorId: String): Topic {
        val now = System.currentTimeMillis()
        val topicId = UUID.randomUUID().toString()
        val topic = Topic(
            id = topicId,
            name = name,
            iconUri = null,
            creatorId = creatorId,
            createdAt = now,
            updatedAt = now
        )
        database.topicQueries.insertTopic(
            id = topic.id,
            name = topic.name,
            icon_uri = topic.iconUri,
            creator_id = topic.creatorId,
            created_at = topic.createdAt,
            updated_at = topic.updatedAt,
            is_deleted = false
        )
        database.topicQueries.insertPersonalState(
            topic_id = topicId,
            user_id = creatorId,
            archived = false,
            pinned = false,
            background = null,
            icon = null,
            last_read_at = null,
            updated_at = now
        )
        database.topicQueries.insertDeviceState(
            topic_id = topicId, font = null
        )
        return topic
    }

    override suspend fun updateTopic(topicId: String, name: String, iconUri: String?) {
        database.topicQueries.updateTopic(
            name = name, icon_uri = iconUri, updated_at = System.currentTimeMillis(), id = topicId
        )
    }

    override suspend fun deleteTopic(topicId: String) {
        database.topicQueries.deleteTopicLogical(
            updated_at = System.currentTimeMillis(), id = topicId
        )
    }

    override suspend fun pinTopic(topicId: String, userId: String, pinned: Boolean) {
        val existing =
            database.topicQueries.getPersonalState(topicId, userId).executeAsList().firstOrNull()
        if (existing != null) {
            database.topicQueries.updatePersonalState(
                archived = existing.archived,
                pinned = pinned,
                background = existing.background,
                icon = existing.icon,
                last_read_at = existing.last_read_at,
                updated_at = System.currentTimeMillis(),
                topic_id = topicId,
                user_id = userId
            )
        }
    }

    override suspend fun archiveTopic(topicId: String, userId: String, archived: Boolean) {
        val existing =
            database.topicQueries.getPersonalState(topicId, userId).executeAsList().firstOrNull()
        if (existing != null) {
            database.topicQueries.updatePersonalState(
                archived = archived,
                pinned = existing.pinned,
                background = existing.background,
                icon = existing.icon,
                last_read_at = existing.last_read_at,
                updated_at = System.currentTimeMillis(),
                topic_id = topicId,
                user_id = userId
            )
        }
    }

    override suspend fun setTopicFont(topicId: String, font: String?) {
        database.topicQueries.updateDeviceState(
            font = font, topic_id = topicId
        )
    }

    override suspend fun setTopicBackground(topicId: String, userId: String, background: String?) {
        val existing =
            database.topicQueries.getPersonalState(topicId, userId).executeAsList().firstOrNull()
        if (existing != null) {
            database.topicQueries.updatePersonalState(
                archived = existing.archived,
                pinned = existing.pinned,
                background = background,
                icon = existing.icon,
                last_read_at = existing.last_read_at,
                updated_at = System.currentTimeMillis(),
                topic_id = topicId,
                user_id = userId
            )
        }
    }

    override fun getTopic(topicId: String): Flow<Topic?> = appPreferences.currentUserId.map { userId ->
        database.topicQueries.getTopicById(topicId).executeAsList().firstOrNull()?.let { row ->
            val personalState =
                database.topicQueries.getPersonalState(row.id, userId ?: "").executeAsList()
                    .firstOrNull()
            Topic(
                id = row.id,
                name = row.name,
                iconUri = row.icon_uri,
                creatorId = row.creator_id,
                createdAt = row.created_at,
                updatedAt = row.updated_at,
                isDeleted = row.is_deleted,
                isPinned = personalState?.pinned ?: false,
                isArchived = personalState?.archived ?: false,
                background = personalState?.background,
                font = database.topicQueries.getDeviceState(row.id).executeAsList()
                    .firstOrNull()?.font
            )
        }
    }
}