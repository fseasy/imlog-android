package top.fseasy.imlog.data.audio.recorder

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.AudioRecordingConfiguration
import android.media.AudioRecordingMonitor
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean
import timber.log.Timber


/**
 * 关闭录音时的 Focus.
 *
 * 一次性使用：和 [RecordingSession] 一样随需随建，不要重复使用。
 */
class RecordingAudioFocusHelper(context: Context) {

  enum class InterruptReason {
    /** 麦克风被系统静音或被更高优先级应用抢占（通话、VoIP、隐私开关等） */
    MIC_SILENCED,
    /** 检测到来电响铃、通话中或通话模式切换 */
    CALL_OR_RINGTONE,
    /** 检测到系统闹钟响起 */
    ALARM,
  }

  private val appContext = context.applicationContext
  private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
  private val mainHandler = Handler(Looper.getMainLooper())
  private val mainExecutor = appContext.mainExecutor

  private var focusRequest: AudioFocusRequest? = null
  private var boundMonitor: AudioRecordingMonitor? = null
  private var recordingCallback: AudioManager.AudioRecordingCallback? = null
  private var playbackCallback: AudioManager.AudioPlaybackCallback? = null
  private var modeChangedListener: AudioManager.OnModeChangedListener? = null

  @Volatile private var interruptCallback: ((InterruptReason) -> Unit)? = null
  private val hasInterrupted = AtomicBoolean(false)
  private val isAbandoned = AtomicBoolean(false)
  private val isRequested = AtomicBoolean(false)

  /**
   * 申请录音独占焦点并开启中断监听（不会因用户手动播放普通音乐而中断，只期望因为闹钟、电话等高优项而被打断）
   *
   * 注意：若传入 [recorder]（AudioRecord 或 MediaRecorder，均实现了 AudioRecordingMonitor）， 必须在调用
   * `startRecording()` / `start()` **之前** 调用本方法，否则系统可能漏掉首次状态回调。
   *
   * 吐掉所有异常
   *
   * @param recorder 可选，强烈建议传入当前正在准备启动的 AudioRecord 或 MediaRecorder 实例
   * @param onInterrupted 真正需要停录时的回调（保证主线程回调，且单次录音周期内最多触发一次）
   * @return true 表示可以开始录音；false 表示当前系统处于通话/响铃/闹钟状态或焦点被拒
   */
  fun requestFocus(
      recorder: AudioRecordingMonitor? = null,
      onInterrupted: ((InterruptReason) -> Unit)? = null,
  ) = runCatching {
    // 一次性使用：若申请过、已被放弃过直接拒绝
    if (!isRequested.compareAndSet(false, true) || isAbandoned.get()) return@runCatching false

    // 1. 前置检查：如果当前已经在通话、响铃或闹钟播放中，直接拒绝开录
    if (isInCallOrRingtoneMode(audioManager.mode)) {
      return@runCatching false
    }
    if (hasBlockingPlayback(audioManager.activePlaybackConfigurations)) {
      return@runCatching false
    }

    // 2. 申请瞬时独占焦点以压停后台音乐，但不监听 AUDIOFOCUS_LOSS
    val attributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

    val request =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(attributes)
            // 故意忽略后续的 AUDIOFOCUS_LOSS，允许用户录音途中手动播放音乐而不停录
            .setOnAudioFocusChangeListener({ /* no-op */ }, mainHandler)
            .build()

    val result = audioManager.requestAudioFocus(request)
    if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
      return@runCatching false
    }

    focusRequest = request
    interruptCallback = onInterrupted

    // 3. 启动真实中断监听
    registerRecordingMonitor(recorder)
    registerPlaybackMonitor()
    registerModeMonitor()

    return@runCatching true
  }
      .onFailure { e ->
        Timber.e(e, "Request focus failed")
        abandonFocus()
      }
      .getOrDefault(false)

  /**
   * 放弃音频焦点并注销所有监听（允许后台音乐恢复播放）
   *
   * 吐掉所有异常
   */
  fun abandonFocus() = runCatching {
    if (!isAbandoned.compareAndSet(false, true)) return@runCatching

    interruptCallback = null

    focusRequest?.let { req ->
      runCatching { audioManager.abandonAudioFocusRequest(req) }
    }
    focusRequest = null

    playbackCallback?.let { cb ->
      audioManager.unregisterAudioPlaybackCallback(cb)
    }
    playbackCallback = null

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      modeChangedListener?.let { listener ->
        audioManager.removeOnModeChangedListener(listener)
      }
    }
    modeChangedListener = null

    recordingCallback?.let { cb ->
      val monitor = boundMonitor
      if (monitor != null) {
        // 防御：避免调用方先 release() 了 recorder 再调用 abandonFocus() 导致异常
        runCatching { monitor.unregisterAudioRecordingCallback(cb) }
      } else {
        audioManager.unregisterAudioRecordingCallback(cb)
      }
    }
    recordingCallback = null
    boundMonitor = null
  }

  private fun registerRecordingMonitor(recorder: AudioRecordingMonitor?) {
    val cb =
        object : AudioManager.AudioRecordingCallback() {
          override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
            val silenced =
                if (recorder != null) {
                  // 绑定到具体 recorder 时，configs 只代表当前会话，直接判断
                  configs.any { it.isClientSilenced }
                } else {
                  // 未传 recorder 时使用全局监听：仅在所有活跃录音都被静默时才触发，避免被其他 App 状态误伤
                  configs.isNotEmpty() && configs.all { it.isClientSilenced }
                }
            if (silenced) {
              dispatchInterrupt(InterruptReason.MIC_SILENCED)
            }
          }
        }

    recordingCallback = cb
    boundMonitor = recorder

    if (recorder != null) {
      recorder.registerAudioRecordingCallback(mainExecutor, cb)
    } else {
      audioManager.registerAudioRecordingCallback(cb, mainHandler)
    }
  }

  private fun registerPlaybackMonitor() {
    val cb =
        object : AudioManager.AudioPlaybackCallback() {
          override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) {
            val reason = findBlockingPlaybackReason(configs) ?: return
            dispatchInterrupt(reason)
          }
        }
    playbackCallback = cb
    audioManager.registerAudioPlaybackCallback(cb, mainHandler)
  }

  private fun registerModeMonitor() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val listener = AudioManager.OnModeChangedListener { mode ->
        if (isInCallOrRingtoneMode(mode)) {
          dispatchInterrupt(InterruptReason.CALL_OR_RINGTONE)
        }
      }
      modeChangedListener = listener
      audioManager.addOnModeChangedListener(mainExecutor, listener)
    }
  }

  private fun dispatchInterrupt(reason: InterruptReason) {
    if (isAbandoned.get()) return
    if (hasInterrupted.compareAndSet(false, true)) {
      mainHandler.post {
        if (isAbandoned.get()) return@post
        interruptCallback?.invoke(reason)
      }
    }
  }

  private fun isInCallOrRingtoneMode(mode: Int): Boolean {
    return mode == AudioManager.MODE_RINGTONE ||
        mode == AudioManager.MODE_IN_CALL ||
        mode == AudioManager.MODE_IN_COMMUNICATION ||
        mode == AudioManager.MODE_CALL_SCREENING
  }

  private fun hasBlockingPlayback(configs: List<AudioPlaybackConfiguration>): Boolean {
    return findBlockingPlaybackReason(configs) != null
  }

  private fun findBlockingPlaybackReason(
      configs: List<AudioPlaybackConfiguration>
  ): InterruptReason? {
    for (config in configs) {
      when (config.audioAttributes.usage) {
        AudioAttributes.USAGE_ALARM -> return InterruptReason.ALARM
        AudioAttributes.USAGE_NOTIFICATION_RINGTONE,
        AudioAttributes.USAGE_VOICE_COMMUNICATION -> return InterruptReason.CALL_OR_RINGTONE
      }
    }
    return null
  }
}
