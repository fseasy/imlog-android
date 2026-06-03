package top.fseasy.imlog.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import top.fseasy.imlog.domain.model.LogScreenTopic
import top.fseasy.imlog.domain.model.Topic
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.TopicPersonalState
import top.fseasy.imlog.domain.model.TopicRole
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.TopicRepository
import top.fseasy.imlog.sqldelight.SqlDelightDb
import top.fseasy.imlog.util.retrySQLiteOnKeyConflict
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.uuid.ExperimentalUuidApi
import top.fseasy.imlog.sqldelight.Topics as TopicEntity
import top.fseasy.imlog.sqldelight.Topic_personal_state as PersonalStateEntity
import top.fseasy.imlog.sqldelight.GetCurrentUserLogScreenTopics as UserLogScreenTopicEntity

@Singleton
class TopicRepositoryImpl @Inject constructor(
    private val database: SqlDelightDb,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TopicRepository {

    override fun observeTopicById(topicId: TopicId): Flow<Topic?> =
        database.topicQueries.getTopicById(topicId.value)
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { it?.toDomain() }
            .flowOn(dispatcher)
            .catch { e ->
                Timber.w(e, "No Topic found for id=${topicId}, emit null")
                emit(null)
            }


    override fun observePersonalStateById(
        userId: UserId,
        topicId: TopicId,
    ): Flow<TopicPersonalState?> =
        database.topicQueries.getPersonalState(topic_id = topicId.value, user_id = userId.value)
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { it?.toDomain() }
            .catch { e ->
                Timber.i(e, "Observe TopicPersonalState failed on id=${topicId}, emit null")
                emit(null)
            }

    /**
     * Used for Log Screen Topics lists (home screen)
     */
    override fun observeLogScreenTopics(userId: UserId): Flow<List<LogScreenTopic>> {
        return database.topicQueries.getCurrentUserLogScreenTopics(userId.value)
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } }
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun createTopic(
        creatorId: UserId, name: String, iconUri: String?,
    ): LogScreenTopic = withContext(dispatcher) {
        val now = System.currentTimeMillis()
        val topicId = retrySQLiteOnKeyConflict {
            TopicId.random()
                .also { newId ->
                    executeInsertTopicTransaction(
                        topicId = newId,
                        topicName = name,
                        iconUri = iconUri,
                        creatorId = creatorId,
                        nowTimestamp = now,
                    )
                }
        }

        LogScreenTopic(
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
        userId: UserId,
        topicId: TopicId,
        newName: String,
    ): Boolean = withContext(dispatcher) {
        val now = System.currentTimeMillis()
        val rowsAffected = database.topicUpdateQueries.updateTopicName(
            newName = newName, updatedAt = now, topicId = topicId.value, userId = userId.value
        ).value;
        rowsAffected > 0L;
    }

    override suspend fun updateTopicIcon(
        userId: UserId,
        topicId: TopicId,
        newIconUri: String,
    ): Boolean = withContext(dispatcher) {
        val now = System.currentTimeMillis()
        val rowsAffected = database.topicUpdateQueries.updateTopicIconUri(
            newIconUri = newIconUri, updatedAt = now, topicId = topicId.value, userId = userId.value
        ).value;
        rowsAffected > 0L;
    }

    override suspend fun updateTopicBackground(
        userId: UserId,
        topicId: TopicId,
        background: String?,
    ): Boolean = withContext(dispatcher) {
        val now = System.currentTimeMillis()
        val rowsAffected = database.topicUpdateQueries.updateTopicPersonalBackground(
            newBackground = background,
            updatedAt = now,
            topicId = topicId.value,
            userId = userId.value
        ).value;
        rowsAffected > 0L;
    }

    override suspend fun pinTopic(userId: UserId, topicId: TopicId, pinned: Boolean): Boolean =
        withContext(dispatcher) {
            val now = System.currentTimeMillis()
            val rowsAffected = database.topicUpdateQueries.updateTopicPersonalPinned(
                newPinned = if (pinned) 1L else 0L,
                updatedAt = now,
                topicId = topicId.value,
                userId = userId.value
            ).value;
            rowsAffected > 0L;
        }

    override suspend fun archiveTopic(
        userId: UserId,
        topicId: TopicId,
        archived: Boolean,
    ): Boolean =
        withContext(dispatcher) {
            val now = System.currentTimeMillis()
            val rowsAffected = database.topicUpdateQueries.updateTopicPersonalArchived(
                newArchived = if (archived) 1L else 0L,
                updatedAt = now,
                topicId = topicId.value,
                userId = userId.value
            ).value;
            rowsAffected > 0L;
        }

    override suspend fun deleteTopic(userId: UserId, topicId: TopicId): Boolean =
        withContext(dispatcher) {
            val now = System.currentTimeMillis()
            val rowsAffected = database.topicUpdateQueries.softDeleteTopic(
                updatedAt = now, topicId = topicId.value, userId = userId.value
            ).value;
            rowsAffected > 0L;
        }

    private fun TopicEntity.toDomain() = Topic(
        id = TopicId(id),
        name = name,
        iconUri = icon_uri,
        creatorId = creator_id?.let(::UserId),
        createdAt = created_at,
        attributesUpdatedAt = attributes_updated_at,
        isDeleted = is_deleted == 1L,
    )

    private fun PersonalStateEntity.toDomain() = TopicPersonalState(
        topicId = TopicId(topic_id),
        userId = UserId(user_id),
        isArchived = archived == 1L,
        isPinned = pinned == 1L,
        background = background,
        lastReadAt = last_read_at ?: System.currentTimeMillis(),
        attributesUpdatedAt = attributes_updated_at
    )

    private fun UserLogScreenTopicEntity.toDomain() = LogScreenTopic(
        id = TopicId(id),
        name = name,
        iconUri = icon_uri,
        isPinned = pinned == 1L,
        hasUnread = has_unread == 1L,
        messageUpdatedAt = topic_message_update_at,
        lastMessageSnippet = last_message_snippet,
        background = background
    )

    private fun executeInsertTopicTransaction(
        topicId: TopicId,
        topicName: String,
        iconUri: String?,
        creatorId: UserId,
        nowTimestamp: Long,
    ) {
        database.transaction {
            // needs to insert to 3 tables: 1. topic 2. personal state 3. topic-members
            database.topicQueries.insertTopic(
                id = topicId.value,
                name = topicName,
                icon_uri = iconUri,
                creator_id = creatorId.value,
                created_at = nowTimestamp,
                attributes_updated_at = nowTimestamp
            )
            database.topicQueries.insertPersonalState(
                topic_id = topicId.value,
                user_id = creatorId.value,
                archived = 0L,
                pinned = 0L,
                background = null,
                last_read_at = nowTimestamp, // set to now when init
                attributes_updated_at = nowTimestamp
            )
            database.topicQueries.insertMember(
                topic_id = topicId.value,
                user_id = creatorId.value,
                user_nickname = null, // use null so it can adapt to the latest name
                role = TopicRole.ADMIN.value,
                joined_at = nowTimestamp,
                attributes_updated_at = nowTimestamp
            )
        }
    }
}