package top.fseasy.imlog.features.home.topiclog.timeline

import android.content.Context
import androidx.annotation.MainThread
import androidx.compose.runtime.Immutable
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.fseasy.imlog.domain.model.MessageId
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds

data class VoiceItem(
    val id: MessageId,
    val path: Path,
    val fileDuration: kotlin.time.Duration, // it's the prior duration.
)

/** 结构化 UI 状态（低频更新：仅在 切换消息、状态变更、倍速、准备完成、出错 时更新） */
@Immutable
data class AudioPlaybackState(
    val playingMessageId: MessageId? = null,
    val isPlaying: Boolean = false,
    val duration: kotlin.time.Duration =
        0.milliseconds, // It's the duration that will be updated by the player (more accurate)
    val playbackSpeed: Float = 1.0f,
    val error: String? = null,
)

/** created & use must be in main thread */
@MainThread
class VoicePlayerManager(
    context: Context,
) : AutoCloseable {

  private val appContext = context.applicationContext

  //  Media3 focus &Becoming Noisy
  private val audioAttributes =
      AudioAttributes.Builder()
          .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
          .setUsage(C.USAGE_MEDIA)
          .build()

  private val exoPlayer =
      ExoPlayer.Builder(appContext)
          .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
          .setHandleAudioBecomingNoisy(true)
          .build()

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var progressJob: Job? = null

  // low frequency state
  private val _playbackState = MutableStateFlow(AudioPlaybackState())
  val playbackState: StateFlow<AudioPlaybackState> = _playbackState.asStateFlow()

  // high frequency state
  private val _currentPositionMs = MutableStateFlow(0L)
  val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

  init {
    exoPlayer.addListener(
        object : Player.Listener {
          override fun onIsPlayingChanged(isPlaying: Boolean) {
            // 唯一的真实播放状态驱动源：真正音频开始播放/暂停时才更新 UI 的 isPlaying
            _playbackState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
              startProgressTracker()
            } else {
              stopProgressTracker()
              // 暂停时立刻同步一次精确位置，消弭 100ms 轮询误差
              syncCurrentPosition()
            }
          }

          override fun onPlaybackStateChanged(state: Int) {
            when (state) {
              Player.STATE_READY -> {
                val duration = exoPlayer.duration
                val realDuration = if (duration != C.TIME_UNSET) duration.coerceAtLeast(0L) else 0L
                _playbackState.update {
                  it.copy(duration = realDuration.milliseconds, error = null)
                }
              }
              Player.STATE_ENDED -> {
                // 播放结束：停止轮询，置 isPlaying 为 false，保留 playingMessageId 方便 UI 显示与重播
                stopProgressTracker()
                _playbackState.update { it.copy(isPlaying = false) }
                _currentPositionMs.update { 0L }
              }
              else -> {}
            }
          }

          override fun onPlayerError(error: PlaybackException) {
            stopProgressTracker()
            _playbackState.update {
              it.copy(
                  isPlaying = false,
                  error = error.localizedMessage ?: "Playback Error",
              )
            }
          }
        }
    )
  }

  /** 播放 / 暂停 控制 */
  fun togglePlayPause(item: VoiceItem) {
    val currentState = _playbackState.value

    if (currentState.playingMessageId == item.id) {
      // 当前语音：仅做 播放 / 暂停 / 重播 切换
      if (exoPlayer.isPlaying) {
        exoPlayer.pause()
      } else {
        if (exoPlayer.playbackState == Player.STATE_ENDED) {
          exoPlayer.seekTo(0)
        }
        exoPlayer.play()
      }
    } else {
      // 非当前语音：切歌并从头播放
      playNewTrack(item, initialPositionMs = 0L)
    }
  }

  /** 按比例 Seek */
  fun seekToRatio(item: VoiceItem, ratio: Float) {
    val currentState = _playbackState.value
    val clampedRatio = ratio.coerceIn(0f, 1f)

    if (currentState.playingMessageId == item.id) {
      // 当前语音：直接 seekTo
      val duration =
          if (currentState.duration.isPositive()) currentState.duration.inWholeMilliseconds
          else exoPlayer.duration.coerceAtLeast(0L)
      if (duration > 0) {
        val targetPosition = (duration * clampedRatio).toLong()
        exoPlayer.seekTo(targetPosition)
        _currentPositionMs.update { targetPosition }

        if (exoPlayer.playbackState == Player.STATE_ENDED) {
          exoPlayer.prepare()
          exoPlayer.play()
        }
      }
    } else {
      // 非当前语音：切歌并直接 Seek 到目标位置播放
      val targetPosition = (item.fileDuration.inWholeMilliseconds * clampedRatio).toLong()
      playNewTrack(item, initialPositionMs = targetPosition)
    }
  }

  /** 切换播放倍速 (1.0x -> 1.5x -> 2.0x) */
  fun changeSpeed(messageId: MessageId) {
    if (_playbackState.value.playingMessageId != messageId) return

    val newSpeed =
        when (_playbackState.value.playbackSpeed) {
          1.0f -> 1.5f
          1.5f -> 2.0f
          else -> 1.0f
        }
    exoPlayer.setPlaybackSpeed(newSpeed)
    _playbackState.update { it.copy(playbackSpeed = newSpeed) }
  }

  /** 核心方法：加载并播放一条全新的语音（包含重置、Seek、 Prepare 和状态更新） */
  private fun playNewTrack(item: VoiceItem, initialPositionMs: Long = 0L) {
    val currentSpeed = _playbackState.value.playbackSpeed

    exoPlayer.stop()
    exoPlayer.clearMediaItems()
    exoPlayer.setMediaItem(MediaItem.fromUri(item.path.toUri()))
    exoPlayer.setPlaybackSpeed(currentSpeed)

    // 如果指定了起始位置（例如从拖拽位置开始播），在 prepare 前设置
    if (initialPositionMs > 0L) {
      exoPlayer.seekTo(initialPositionMs)
    }

    // 状态更新统一归集到此处
    _playbackState.update {
      it.copy(
          playingMessageId = item.id,
          isPlaying = false, // Single Source of Truth，等待 onIsPlayingChanged 回调驱动
          duration = item.fileDuration,
          error = null,
      )
    }
    exoPlayer.prepare()
    exoPlayer.play()
    _currentPositionMs.update { initialPositionMs }
  }

  private fun startProgressTracker() {
    stopProgressTracker()
    progressJob = scope.launch {
      while (isActive) {
        syncCurrentPosition()
        delay(100.milliseconds)
      }
    }
  }

  private fun stopProgressTracker() {
    progressJob?.cancel()
    progressJob = null
  }

  private fun syncCurrentPosition() {
    _currentPositionMs.update { exoPlayer.currentPosition.coerceAtLeast(0L) }
  }

  /** 主动重置所有播放状态（例如离开当前会话页面时调用） */
  fun stopAndReset() {
    stopProgressTracker()
    exoPlayer.stop()
    exoPlayer.clearMediaItems()
    _playbackState.value = AudioPlaybackState()
    _currentPositionMs.update { 0L }
  }

  override fun close() {
    stopAndReset()
    scope.cancel()
    exoPlayer.release()
  }
}
