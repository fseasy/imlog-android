package top.fseasy.imlog.data.util

import android.content.Context
import android.net.Uri
import androidx.annotation.MainThread
import androidx.compose.runtime.Immutable
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

data class MediaInput(
    val id: String,
    val uri: Uri,
    val fileDuration: Duration, // it's the prior duration.
)

enum class PlayerStatus {
  Idle,
  Buffering,
  Playing,
  Paused,
  Error,
}

// PlayingPosition isn't included here as it's high frequency state
@Immutable
data class MediaPlaybackState(
    val playingId: String? = null,
    val status: PlayerStatus = PlayerStatus.Idle,
    // It's the duration that will be updated by the player (more accurate)
    val duration: Duration = 0.milliseconds,
    val speed: Float = 1.0f,
    val error: String? = null,
) {
  fun isThisMediaActive(id: String) = playingId == id

  fun isThisMediaPlaying(id: String) = isThisMediaActive(id) && status == PlayerStatus.Playing
}

/** created & use must be in main thread as ExoPlayer requirements */
@UnstableApi
@MainThread
class ExoPlayerStateHolder(
    context: Context,
    private val onPlayingStopped: (state: MediaPlaybackState, playPosition: Duration) -> Unit =
        { _, _ ->
        },
) : AutoCloseable {

  // low frequency state
  private val _playbackState = MutableStateFlow(MediaPlaybackState())
  val playbackState: StateFlow<MediaPlaybackState> = _playbackState.asStateFlow()

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

  private val renderersFactory = DefaultRenderersFactory(context).apply {
    setEnableDecoderFallback(true)
  }

  val exoPlayer =
      ExoPlayer.Builder(appContext, renderersFactory)
          .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
          .setHandleAudioBecomingNoisy(true)
          .build()

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var progressJob: Job? = null

  init {
    /** All the PlaybackState updating will be limited to the listener! */
    exoPlayer.addListener(
        object : Player.Listener {

          override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // update all the states except the playback speed
            getInputFromMediaItemTag(mediaItem)?.let { item ->
              // Keep the speed, then reset state to another
              _playbackState.update {
                it.copy(
                    playingId = item.id,
                    status = getCurrentExoPlayerPlaybackStatus(),
                    duration = item.fileDuration,
                    error = null,
                )
              }
            }
          }

          override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
              _playbackState.update { it.copy(status = PlayerStatus.Playing) }
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
                  status = PlayerStatus.Error,
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
  fun togglePlayPause(input: MediaInput) {
    val currentState = _playbackState.value

    if (currentState.playingId == input.id) {
      if (exoPlayer.isPlaying) {
        exoPlayer.pause()
      } else {
        if (exoPlayer.playbackState == Player.STATE_ENDED) {
          exoPlayer.seekTo(0)
        }
        exoPlayer.play()
      }
    } else {
      playNewTrack(input, initialPositionMs = 0L)
    }
  }

  /** seek based on ratio, can be used on the same or different media target */
  fun seekToRatio(item: MediaInput, ratio: Float) {
    val currentState = _playbackState.value
    val clampedRatio = ratio.coerceIn(0f, 1f)

    if (currentState.playingId == item.id) {
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

  /** Playback Speed is a global status. */
  fun changeSpeed(speed: Float) {
    exoPlayer.setPlaybackSpeed(speed)
  }

  fun stopAndReset() {
    stopUpdatePlayPosition()
    exoPlayer.stop()
    exoPlayer.clearMediaItems()
    _playbackState.update { MediaPlaybackState() }
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

  private fun playNewTrack(input: MediaInput, initialPositionMs: Long = 0L) {
    val currentSpeed = _playbackState.value.speed

    exoPlayer.stop()
    exoPlayer.clearMediaItems()

    val mediaItem =
        MediaItem.Builder().setMediaId(input.id).setTag(input).setUri(input.uri.toString()).build()

    exoPlayer.setMediaItem(mediaItem)
    exoPlayer.setPlaybackSpeed(currentSpeed)

    if (initialPositionMs > 0L) {
      exoPlayer.seekTo(initialPositionMs)
    }

    exoPlayer.prepare()
    exoPlayer.play()
  }

  private fun getCurrentExoPlayerPlaybackStatus(): PlayerStatus {
    return when {
      exoPlayer.playerError != null -> PlayerStatus.Error
      // play clicked (playWhenReady=true)，while ExoPlayer is still in STATE_BUFFERING
      exoPlayer.playbackState == Player.STATE_BUFFERING && exoPlayer.playWhenReady ->
          PlayerStatus.Buffering
      exoPlayer.isPlaying -> PlayerStatus.Playing
      exoPlayer.playbackState == Player.STATE_READY -> PlayerStatus.Paused
      else -> PlayerStatus.Idle
    }
  }

  private fun getInputFromMediaItemTag(mediaItem: MediaItem?): MediaInput? =
      mediaItem?.localConfiguration?.tag as? MediaInput

  private fun getExoPlayerCurrentPosition() =
      exoPlayer.currentPosition.coerceAtLeast(0L).milliseconds
}
