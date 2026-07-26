package top.fseasy.imlog.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import top.fseasy.imlog.domain.model.HomeTopic
import top.fseasy.imlog.domain.model.Topic
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.TopicPersonalState
import top.fseasy.imlog.domain.model.TopicMemberRole
import top.fseasy.imlog.domain.model.TopicWithPersonalPreference
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.TopicRepository
import top.fseasy.imlog.sqldelight.SqlDelightDb
import top.fseasy.imlog.data.util.retrySQLiteOnKeyConflict
import top.fseasy.imlog.domain.model.AvatarModel
import top.fseasy.imlog.domain.model.MessageDraft
import top.fseasy.imlog.domain.model.defaultTopicPresetAvatar
import top.fseasy.imlog.domain.model.serialize
import top.fseasy.imlog.domain.model.toAvatarModelOrNull
import top.fseasy.imlog.sqldelight.Topic_message_state
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import top.fseasy.imlog.sqldelight.GetCurrentUserHomeScreenTopics as HomeTopicEntity
import top.fseasy.imlog.sqldelight.GetTopicWithPersonalPreference as GetTopicWithPersonalPreferenceEntity
import top.fseasy.imlog.sqldelight.Topic_personal_preference as PersonalStatePreference
import top.fseasy.imlog.sqldelight.Topic_members as TopicMemberEntity
import top.fseasy.imlog.sqldelight.Topics as TopicEntity

@Singleton
class TopicRepositoryImpl @Inject constructor(
    private val database: SqlDelightDb,
    private val dispatcher: CoroutineDispatcher,
) : TopicRepository {

    override fun observeTopic(topicId: TopicId): Flow<Topic?> =
        database.topicSelectQueries.getTopicById(topicId)
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { it?.toDomain() }
            .catch { e ->
                Timber.w(e, "No Topic found for id=${topicId}, emit null")
                emit(null)
            }

    override fun observeTopicPersonalPreference(
        userId: UserId,
        topicId: TopicId,
    ): Flow<TopicPersonalState?> = database.topicSelectQueries.getPersonalState(
        topic_id = topicId, user_id = userId
    )
        .asFlow()
        .mapToOneOrNull(dispatcher)
        .map { it?.toDomain() }
        .catch { e ->
            Timber.i(e, "Observe TopicPersonalState failed on id=${topicId}, emit null")
            emit(null)
        }

    override fun observeTopicWithPersonalState(
        topicId: TopicId,
        userId: UserId,
    ): Flow<TopicWithPersonalPreference?> =
        database.topicSelectQueries.getTopicWithPersonalPreference(
            topic_id = topicId, user_id = userId
        )
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { it?.toDomain() }
            .catch { e ->
                Timber.i(e, "Observer TopicWithPersonalState failed on id=$topicId")
                emit(null)
            }

    /**
     * Used for Log Screen Topics lists (home screen)
     */
    override fun observeHomeTopics(userId: UserId): Flow<List<HomeTopic>> {
        return database.topicSelectQueries.getCurrentUserHomeScreenTopics(userId)
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } }
    }

    /**
     * @see top.fseasy.imlog.domain.usecase.WelcomeUseCase
     */
    override suspend fun countAllRelatedTopicsForUser(userId: UserId): Long =
        withContext(dispatcher) {
            database.topicSelectQueries.countAllRelatedTopicsForUser(userId)
                .executeAsOne()
        }

    /**
     * NOTE: it's SYNC. Not In IO thread, not in transaction.
     *      It's expected to be used in withContext(IO) and transaction block!
     * @throws Exception
     */
    override fun syncCreateNewTopic(
        creatorId: UserId, name: String, avatarModel: AvatarModel, description: String?,
        createdAtTimestampMs: Long,
    ): TopicId {
        val topicId = TopicId.random()

        // needs to insert to 3 tables: 1. topic 2. personal state 3. topic-members
        database.topicQueries.insertTopic(
            TopicEntity(
                id = topicId,
                name = name,
                avatar_model = avatarModel.serialize(),
                description = description,
                creator_id = creatorId,
                created_at = createdAtTimestampMs,
                attributes_updated_at = createdAtTimestampMs,
            )
        )
        database.topicQueries.insertPersonalPreference(
            PersonalStatePreference(
                topic_id = topicId,
                user_id = creatorId,
                archived = false,
                pinned = false,
                background = null,
                attributes_updated_at = createdAtTimestampMs
            )
        )
        database.topicQueries.insertMember(
            TopicMemberEntity(
                topic_id = topicId,
                user_id = creatorId,
                user_nickname = null, // use null so it can adapt to the latest name
                role = TopicMemberRole.Admin,
                joined_at = createdAtTimestampMs,
                attributes_updated_at = createdAtTimestampMs
            )
        )
        database.topicQueries.insertMessageState(
            Topic_message_state(
                topic_id = topicId,
                user_id = creatorId,
                last_read_at = createdAtTimestampMs,
                latest_message_at = createdAtTimestampMs,
                latest_message_preview = null,
                draft = null
            )
        )
        return topicId
    }

    override suspend fun createNewTopic(
        creatorId: UserId, name: String, avatarModel: AvatarModel, description: String?,
        createdAtTimestampMs: Long,
    ): TopicId = withContext(dispatcher) {
        retrySQLiteOnKeyConflict {
            database.transactionWithResult {
                syncCreateNewTopic(
                    creatorId = creatorId,
                    name = name,
                    avatarModel = avatarModel,
                    description = description,
                    createdAtTimestampMs = createdAtTimestampMs
                )
            }
        }
    }

    override suspend fun updateTopicName(
        userId: UserId,
        topicId: TopicId,
        newName: String,
    ): Boolean = withContext(dispatcher) {
        val now = System.currentTimeMillis()
        val rowsAffected = database.topicUpdateQueries.updateTopicName(
            newName = newName,
            updatedAt = now,
            topicId = topicId,
            triggerUserId = userId,
            adminRoleValue = TopicMemberRole.Admin
        ).value;
        rowsAffected > 0L;
    }

    override suspend fun updateAvatarModel(
        userId: UserId,
        topicId: TopicId,
        newAvatarModel: AvatarModel,
    ): Boolean = withContext(dispatcher) {
        val now = System.currentTimeMillis()
        val rowsAffected = database.topicUpdateQueries.updateTopicAvatarModel(
            newAvatarModel = newAvatarModel.serialize(),
            updatedAt = now,
            topicId = topicId,
            triggerUserId = userId,
            adminRoleValue = TopicMemberRole.Admin
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
            newBackground = background, updatedAt = now, topicId = topicId, triggerUserId = userId
        ).value;
        rowsAffected > 0L;
    }

    override suspend fun pinTopic(userId: UserId, topicId: TopicId, pinned: Boolean): Boolean =
        withContext(dispatcher) {
            val now = System.currentTimeMillis()
            val rowsAffected = database.topicUpdateQueries.updateTopicPersonalPinned(
                newPinned = pinned, updatedAt = now, topicId = topicId, triggerUserId = userId
            ).value;
            rowsAffected > 0L;
        }

    override suspend fun archiveTopic(
        userId: UserId,
        topicId: TopicId,
        archived: Boolean,
    ): Boolean = withContext(dispatcher) {
        val now = System.currentTimeMillis()
        val rowsAffected = database.topicUpdateQueries.updateTopicPersonalArchived(
            newArchived = archived, updatedAt = now, topicId = topicId, triggerUserId = userId
        ).value;
        rowsAffected > 0L;
    }

    override suspend fun deleteTopic(userId: UserId, topicId: TopicId): Boolean =
        withContext(dispatcher) {
            val rowsAffected = database.topicQueries.deleteTopic(
                topicId = topicId, triggerUserId = userId, adminRoleValue = TopicMemberRole.Admin
            ).value;
            rowsAffected > 0L;
        }

    override suspend fun getMessageDraft(userId: UserId, topicId: TopicId): MessageDraft? =
        withContext(
            Dispatchers.IO
        ) {
            database.topicSelectQueries.getMessageDraft(topicId = topicId, userId = userId)
                .executeAsOneOrNull()?.draft?.getOrNull()
        }

    private fun TopicEntity.toDomain() = Topic(
        id = id,
        name = name,
        avatarModel = avatar_model.toAvatarModelOrNull() ?: defaultTopicPresetAvatar(),
        description = description,
        creatorId = creator_id,
        createdAt = created_at,
        attributesUpdatedAt = attributes_updated_at,
    )

    private fun PersonalStatePreference.toDomain() = TopicPersonalState(
        topicId = topic_id,
        userId = user_id,
        isArchived = archived,
        isPinned = pinned,
        background = background,
        attributesUpdatedAt = attributes_updated_at
    )

    private fun HomeTopicEntity.toDomain() = HomeTopic(
        id = id,
        name = name,
        avatarModel = avatar_model.toAvatarModelOrNull() ?: defaultTopicPresetAvatar(),
        description = description,
        isPinned = pinned == 1L,
        hasUnread = has_unread == 1L,
        messageUpdatedAt = topic_message_update_at,
        latestMessagePreview = latest_message_preview?.getOrNull(),
        draft = draft?.getOrNull()
    )

    private fun GetTopicWithPersonalPreferenceEntity.toTopicEntity() = TopicEntity(
        id = id,
        name = name,
        avatar_model = avatar_model,
        description = description,
        creator_id = creator_id,
        created_at = created_at,
        attributes_updated_at = attributes_updated_at,
    )

    private fun GetTopicWithPersonalPreferenceEntity.toDomain() = TopicWithPersonalPreference(
        topic = toTopicEntity().toDomain(),
        isArchived = archived ?: false,
        isPinned = pinned ?: false,
        background = background,
    )
}