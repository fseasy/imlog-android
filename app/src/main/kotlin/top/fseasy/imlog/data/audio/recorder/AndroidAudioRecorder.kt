package top.fseasy.imlog.data.audio.recorder

import android.app.Application
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.fseasy.imlog.common.launchCatching
import top.fseasy.imlog.domain.audio.AudioRecordException
import top.fseasy.imlog.domain.audio.AudioRecordResult
import top.fseasy.imlog.domain.audio.AudioRecorder
import top.fseasy.imlog.domain.audio.RecordingOsEvent
import top.fseasy.imlog.domain.audio.RecordingSnapshot
import top.fseasy.imlog.domain.model.StoragePathModel

class AndroidAudioRecorder(
    private val application: Application,
    private val scope: CoroutineScope,
    private val sampleInterval: Duration = 100.milliseconds,
) : AudioRecorder {

  private val _snapshot = MutableStateFlow(RecordingSnapshot())
  override val snapshot: StateFlow<RecordingSnapshot> = _snapshot.asStateFlow()

  private val mutex = Mutex()
  // 所有易变状态均在 session 内部，activeSession != null 即代表正在录音
  @Volatile private var activeSession: RecordingSession? = null

  /**
   * Run IO operations in IO thread.
   *
   * @throws kotlinx.coroutines.CancellationException
   * @throws AudioRecordException
   */
  override suspend fun start(
      destinationPath: StoragePathModel.Internal,
      onOsEvent: (RecordingOsEvent) -> Unit,
  ) = mutex.withLock {
    if (activeSession != null) {
      throw AudioRecordException.fromIllegalStateException(
          "Invalid state: start with active session",
      )
    }
    val newSession =
        RecordingSession.start(
            context = application,
            scope = scope,
            destinationPath = destinationPath,
            sampleInterval = sampleInterval,
            onSnapshot = { duration, pulse ->
              _snapshot.update { s ->
                if (s.status == RecordingSnapshot.Status.Recording) {
                  s.copy(duration = duration, currentPulse = pulse)
                } else {
                  s
                }
              }
            },
            onOsEvent = onOsEvent,
        )
    activeSession = newSession
    // Update only when it really started!
    _snapshot.value = RecordingSnapshot(status = RecordingSnapshot.Status.Recording)
  }

  /**
   * @throws kotlinx.coroutines.CancellationException
   * @throws AudioRecordException
   */
  override suspend fun stop() = mutex.withLock {
    val session =
        activeSession
            ?: throw AudioRecordException.fromIllegalStateException(
                "Invalid State: call stop while activeSession = null",
            )
    activeSession = null
    stopSessionLocked(session)
  }

  /**
   * Run in IO thread for necessary parts.
   *
   * @throws kotlinx.coroutines.CancellationException
   * @throws AudioRecordException
   */
  override suspend fun cancel() = mutex.withLock {
    val session =
        activeSession
            ?: throw AudioRecordException.fromIllegalStateException(
                "Invalid State: call Cancel while no active session",
            )
    activeSession = null
    val result =
        try {
          session.cancel()
        } finally {
          _snapshot.value = RecordingSnapshot(status = RecordingSnapshot.Status.Idle)
        }
    result
  }

  override fun close() {
    CoroutineScope(Dispatchers.IO).launchCatching {
      mutex.withLock {
        _snapshot.value = RecordingSnapshot(status = RecordingSnapshot.Status.Idle)
        val currentSession = activeSession ?: return@withLock
        activeSession = null
        currentSession.cancel()
      }
    }
  }

  override val outputAudioFileExtension: String = ".aac"
  override val outputAudioMimeType: String = "audio/aac"

  /** Call this within mutex LOCK! */
  private suspend fun stopSessionLocked(session: RecordingSession): AudioRecordResult {
    return try {
      session.stop()
    } finally {
      _snapshot.update { s ->
        s.copy(
            status = RecordingSnapshot.Status.Idle,
            currentPulse = 0f,
        )
      }
    }
  }
}
