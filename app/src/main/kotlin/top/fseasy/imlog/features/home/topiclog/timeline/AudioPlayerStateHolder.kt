package top.fseasy.imlog.features.home.topiclog.timeline

import android.content.Context
import androidx.annotation.MainThread
import androidx.compose.runtime.Immutable
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
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

data class AudioInput(
    val id: MessageId,
    val path: Path,
    val fileDuration: Duration, // it's the prior duration.
)

enum class PlaybackStatus {
  Idle,
  Buffering,
  Playing,
  Paused,
  Error,
}

// PlayingPosition isn't included here as it's high frequency state
@Immutable
data class AudioPlaybackState(
    val playingMessageId: MessageId? = null,
    val status: PlaybackStatus = PlaybackStatus.Idle,
    // It's the duration that will be updated by the player (more accurate)
    val duration: Duration = 0.milliseconds,
    val speed: Float = 1.0f,
    val error: String? = null,
) {
  fun isThisMessageActive(messageId: MessageId) = playingMessageId == messageId

  fun isThisMessagePlaying(messageId: MessageId) =
      playingMessageId == messageId && status == PlaybackStatus.Playing
}

/** created & use must be in main thread */
@MainThread
class AudioPlayerStateHolder(
    context: Context,
    private val onPlayingStopped: (state: AudioPlaybackState, playPosition: Duration) -> Unit =
        { _, _ ->
        },
) : AutoCloseable {

  // low frequency state
  private val _playbackState = MutableStateFlow(AudioPlaybackState())
  val playbackState: StateFlow<AudioPlaybackState> = _playbackState.asStateFlow()

  // high frequency state
  private val _playPositionState = MutableStateFlow(0.milliseconds)
  val playPositionState: StateFlow<Duration> = _playPositionState.asStateFlow()

  private val appContext = context.applicationContext

  //  Media3 focus & Becoming Noisy
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

  init {
    /** All the AudioPlaybackState updating will be limited to the listener! */
    exoPlayer.addListener(
        object : Player.Listener {

          override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // update all the states except the playback speed
            getInputFromMediaItemTag(mediaItem)?.let { item ->
              // Keep the speed, then reset state to another
              _playbackState.update {
                it.copy(
                    playingMessageId = item.id,
                    status = getCurrentExoPlayerPlaybackStatus(),
                    duration = item.fileDuration,
                    error = null,
                )
              }
            }
          }

          override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
              _playbackState.update { it.copy(status = PlaybackStatus.Playing) }
              startUpdatePlayPosition()
            } else {
              _playbackState.update { it.copy(status = getCurrentExoPlayerPlaybackStatus()) }
              stopUpdatePlayPosition()
              val currentPosition = getExoPlayerCurrentPosition()
              _playPositionState.update { currentPosition }
              onPlayingStopped(_playbackState.value, currentPosition)
            }
          }

          override fun onPlaybackStateChanged(state: Int) {
            val status = getCurrentExoPlayerPlaybackStatus()
            when (state) {
              Player.STATE_READY -> {
                val duration = exoPlayer.duration
                val realDuration = if (duration != C.TIME_UNSET) duration.coerceAtLeast(0L) else 0L
                _playbackState.update {
                  it.copy(status = status, duration = realDuration.milliseconds, error = null)
                }
              }
              else -> {
                _playbackState.update { it.copy(status = status) }
              }
            }
          }

          override fun onPlayerError(error: PlaybackException) {
            stopUpdatePlayPosition()
            _playbackState.update {
              it.copy(
                  status = PlaybackStatus.Error,
                  error = error.localizedMessage ?: "Playback Error",
              )
            }
          }

          override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            val speed = playbackParameters.speed
            _playbackState.update { it.copy(speed = speed) }
          }
        }
    )
  }

  /**
   * - If operation on current selected media: pause / replay;
   * - else, stop current and switch to the new input
   */
  fun togglePlayPause(item: AudioInput) {
    val currentState = _playbackState.value

    if (currentState.playingMessageId == item.id) {
      if (exoPlayer.isPlaying) {
        exoPlayer.pause()
      } else {
        if (exoPlayer.playbackState == Player.STATE_ENDED) {
          exoPlayer.seekTo(0)
        }
        exoPlayer.play()
      }
    } else {
      playNewTrack(item, initialPositionMs = 0L)
    }
  }

  /** seek based on ratio, can be used on the same or different media target */
  fun seekToRatio(item: AudioInput, ratio: Float) {
    val currentState = _playbackState.value
    val clampedRatio = ratio.coerceIn(0f, 1f)

    if (currentState.playingMessageId == item.id) {
      val duration = currentState.duration.inWholeMilliseconds
      if (duration > 0L) {
        val targetPosition = (duration * clampedRatio).toLong()
        exoPlayer.seekTo(targetPosition)
        // trigger play if it's already reach the end
        if (exoPlayer.playbackState == Player.STATE_ENDED) {
          exoPlayer.prepare()
          exoPlayer.play()
        }
      }
    } else {
      val targetPosition = (item.fileDuration.inWholeMilliseconds * clampedRatio).toLong()
      playNewTrack(item, initialPositionMs = targetPosition)
    }
  }

  /** Change Speed in range of {1, 1.5, 2} */
  fun changeSpeed(messageId: MessageId) {
    if (_playbackState.value.playingMessageId != messageId) return

    val newSpeed =
        when (_playbackState.value.speed) {
          1.0f -> 1.5f
          1.5f -> 2.0f
          else -> 1.0f
        }
    exoPlayer.setPlaybackSpeed(newSpeed)
  }

  fun stopAndReset() {
    stopUpdatePlayPosition()
    exoPlayer.stop()
    exoPlayer.clearMediaItems()
    _playbackState.update { AudioPlaybackState() }
    _playPositionState.update { 0.milliseconds }
  }

  override fun close() {
    stopAndReset()
    scope.cancel()
    exoPlayer.release()
  }

  private fun startUpdatePlayPosition() {
    stopUpdatePlayPosition()
    progressJob = scope.launch {
      while (isActive) {
        _playPositionState.update { getExoPlayerCurrentPosition() }
        delay(100.milliseconds)
      }
    }
  }

  private fun stopUpdatePlayPosition() {
    progressJob?.cancel()
    progressJob = null
  }

  private fun playNewTrack(item: AudioInput, initialPositionMs: Long = 0L) {
    val currentSpeed = _playbackState.value.speed

    exoPlayer.stop()
    exoPlayer.clearMediaItems()

    val mediaItem =
        MediaItem.Builder()
            .setMediaId(item.id.toString())
            .setTag(item)
            .setUri(item.path.toString())
            .build()

    exoPlayer.setMediaItem(mediaItem)
    exoPlayer.setPlaybackSpeed(currentSpeed)

    if (initialPositionMs > 0L) {
      exoPlayer.seekTo(initialPositionMs)
    }

    exoPlayer.prepare()
    exoPlayer.play()
  }

  private fun getCurrentExoPlayerPlaybackStatus(): PlaybackStatus {
    return when {
      exoPlayer.playerError != null -> PlaybackStatus.Error
      // play clicked (playWhenReady=true)，while ExoPlayer is still in STATE_BUFFERING
      exoPlayer.playbackState == Player.STATE_BUFFERING && exoPlayer.playWhenReady ->
          PlaybackStatus.Buffering
      exoPlayer.isPlaying -> PlaybackStatus.Playing
      exoPlayer.playbackState == Player.STATE_READY -> PlaybackStatus.Paused
      else -> PlaybackStatus.Idle
    }
  }

  private fun getInputFromMediaItemTag(mediaItem: MediaItem?): AudioInput? =
      mediaItem?.localConfiguration?.tag as? AudioInput

  private fun getExoPlayerCurrentPosition() =
      exoPlayer.currentPosition.coerceAtLeast(0L).milliseconds
}
