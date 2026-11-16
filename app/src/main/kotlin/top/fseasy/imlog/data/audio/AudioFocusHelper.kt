package top.fseasy.imlog.data.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

class AudioFocusHelper(context: Context) {

  private val audioManager =
      context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

  private var focusRequest: AudioFocusRequest? = null
  private var activeListener: AudioManager.OnAudioFocusChangeListener? = null

  /**
   * 申请瞬时独占音频焦点
   *
   * @param onFocusLost 录音过程中突然失去焦点时的回调（如来电、闹钟响起等），用于及时停录
   * @return true 表示申请成功，可以开始录音；false 表示被系统拒绝（例如当前正在通话中）
   */
  fun requestFocus(onFocusLost: (() -> Unit)? = null): Boolean {
    // 先确保上一次残留的焦点已清理，保证幂等
    abandonFocus()

    val listener = AudioManager.OnAudioFocusChangeListener { focusChange ->
      when (focusChange) {
        AudioManager.AUDIOFOCUS_LOSS,
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
          // 录音中途被系统电话、其他播放器或闹钟抢走了焦点
          onFocusLost?.invoke()
        }
      }
    }
    activeListener = listener

    val attributes =
        AudioAttributes.Builder()
            // 录音配合焦点申请使用 USAGE_MEDIA + SPEECH 即可，避免被 OEM 误判成 VoIP 通话链路
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

    val request =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(attributes)
            // 显式指定回调在主线程（避免如果在无 Looper 的后台线程调用引发问题）
            .setOnAudioFocusChangeListener(listener, Handler(Looper.getMainLooper()))
            .build()

    focusRequest = request
    val result = audioManager.requestAudioFocus(request)

    val granted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    if (!granted) {
      // 申请失败时清理引用
      focusRequest = null
      activeListener = null
    }
    return granted
  }

  /** 放弃音频焦点（让后台音乐恢复播放） */
  fun abandonFocus() {
    focusRequest?.let { req ->
      audioManager.abandonAudioFocusRequest(req)
    }
    focusRequest = null
    activeListener = null
  }
}
