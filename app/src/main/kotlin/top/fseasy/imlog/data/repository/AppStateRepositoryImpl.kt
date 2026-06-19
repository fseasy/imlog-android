package top.fseasy.imlog.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import timber.log.Timber
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.AppStateRepository
import top.fseasy.imlog.sqldelight.SqlDelightDb
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class AppStateRepositoryImpl @Inject constructor(
    private val database: SqlDelightDb,
    private val dispatcher: CoroutineDispatcher,
) : AppStateRepository {
    /**
     * proxy by UserRepo
     */
    @ApiStatus.Internal
    override fun observeCurrentUserIdOrNull(): Flow<UserId?> {
        return database.appStateQueries
            .getByKey(StateKey.CURRENT_USER_ID.rawKey)
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { it?.value_?.let(::UserId) }
            .distinctUntilChanged()
            .catch { e ->
                Timber.e(e, "Observer current user id failed")
                emit(null)
            }
    }

    @ApiStatus.Internal
    override suspend fun setCurrentId(userId: UserId) = withContext(dispatcher) {
        put(StateKey.CURRENT_USER_ID, userId.value)
    }

    /**
     * A blocking version to set current id, typically used in transaction.
     */
    @ApiStatus.Internal
    override fun syncSetCurrentId(userId: UserId) {
        put(StateKey.CURRENT_USER_ID, userId.value)
    }

    private fun getString(key: StateKey, default: String = ""): String =
        database.appStateQueries.getByKey(key.rawKey)
            .executeAsOneOrNull()?.value_ ?: default

    private fun getBoolean(key: StateKey, default: Boolean = false): Boolean =
        getString(key).toBooleanStrictOrNull() ?: default

    private fun getInt(key: StateKey, default: Int = 0): Int =
        getString(key).toIntOrNull() ?: default

    private fun <T> put(key: StateKey, value: T?) {
        if (value == null) {
            database.appStateQueries.removeByKey(key.rawKey)
        } else {
            database.appStateQueries.upsert(key.rawKey, value.toString())
        }
    }
}

private enum class StateKey(val rawKey: String) {
    CURRENT_USER_ID("current_user_id");
}