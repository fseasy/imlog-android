package top.fseasy.imlog.features.home.conversation.composer2.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import top.fseasy.imlog.common.TransitionDecision
import top.fseasy.imlog.common.TransitionResult
import top.fseasy.imlog.common.launchCatching
import top.fseasy.imlog.common.runSuspendCatching
import top.fseasy.imlog.common.tryTransition
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.model.VoiceDraft

/**
 * 管理 VoiceDraftUiState:
 *
 * 1. 状态转移
 * 2. 状态落库
 */
internal class VoiceDraftUiStateStore(
    private val appScope: CoroutineScope,
    private val persistDraft: suspend (userId: UserId, draft: VoiceDraft?) -> Unit,
) {
  private val _state = MutableStateFlow<VoiceDraftUiState>(VoiceDraftUiState.Initializing)
  val state: StateFlow<VoiceDraftUiState> = _state.asStateFlow()

  private val dbPersistMutex = Mutex()
  private var lastPersistedDraft: VoiceDraft? = null
  private var authenticatedUserId: UserId? = null

  /** init 加载与自愈完成后调用一次，建立基准并离开 Initializing - 更新内部状态，不会写库，用于从 db 里已加载、修正出正确状态时调用 */
  suspend fun markInitialized(
      userId: UserId,
      initialDraft: VoiceDraft.Committed?,
  ) {
    val initUiState =
        if (initialDraft == null) VoiceDraftUiState.Idle
        else VoiceDraftUiState.CommittedDraft(initialDraft)
    dbPersistMutex.withLock {
      authenticatedUserId = userId
      lastPersistedDraft = initialDraft
      _state.update { initUiState }
    }
  }

  /** 原子 CAS 状态迁移；若待持久化草稿发生变化，自动异步触发写库 - 使用 appScope 异步更新 */
  fun tryTransition(
      decide: (current: VoiceDraftUiState) -> TransitionDecision<VoiceDraftUiState>,
  ): TransitionResult<VoiceDraftUiState> {
    val result = _state.tryTransition(decide)
    if (
        result is TransitionResult.Done &&
            result.previous.toPersistableDraftOrNull() != result.current.toPersistableDraftOrNull()
    ) {
      schedulePersistLatest()
    }
    return result
  }

  /** 适用于已经在 appScope + NonCancellable 内的挂起迁移（如 stop / cancel 收尾）- 更新时阻塞 */
  suspend fun updateAndPersist(nextState: VoiceDraftUiState) {
    _state.update { nextState }
    persistLatestNow()
  }

  /**
   * 显式等待当前最新状态完成落库（用于“先写 WAL 再开录”或“先清 DB 再删文件”） - 更新时阻塞，所以是 suspend
   *
   * 在锁内部，总是读取当前 state 的最终状态，且写之前比对、写之后更新 —— 重复状态可以无 IO 跳过。
   *
   * Main-thread safe.
   */
  suspend fun persistLatestNow() {
    val userId = authenticatedUserId ?: return
    withContext(NonCancellable + Dispatchers.IO) {
      dbPersistMutex.withLock {
        val current = _state.value
        if (current is VoiceDraftUiState.Initializing) return@withLock
        val targetDraft = current.toPersistableDraftOrNull()
        if (targetDraft != lastPersistedDraft) {
          runSuspendCatching { persistDraft(userId, targetDraft) }
              .onSuccess { lastPersistedDraft = targetDraft }
              .onFailure { e -> Timber.e(e, "Failed to persist voice draft") }
        }
      }
    }
  }

  private fun schedulePersistLatest() = appScope.launchCatching { persistLatestNow() }

  private fun VoiceDraftUiState.toPersistableDraftOrNull(): VoiceDraft? =
      when (this) {
        VoiceDraftUiState.Initializing,
        VoiceDraftUiState.Idle,
        is VoiceDraftUiState.ActiveRecording.Holding -> null
        is VoiceDraftUiState.ActiveRecording.Locked -> draft
        is VoiceDraftUiState.CommittedDraft -> draft
      }
}
