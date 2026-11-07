package top.fseasy.imlog.data.recorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import top.fseasy.imlog.ImLogApplication
import top.fseasy.imlog.R
import top.fseasy.imlog.data.constants.APP_STATIC_NAME
import top.fseasy.imlog.domain.recorder.RecordingSnapshot
import top.fseasy.imlog.domain.recorder.VoiceRecordingCoordinator
import top.fseasy.imlog.domain.util.secondsToAppMessageTimeFormat

class VoiceRecordingService : Service() {

  companion object {
    const val CHANNEL_ID = "voice_recording_service_channel"
    const val NOTIFICATION_ID = 29327

    const val ACTION_GUARD = "ACTION_GUARD"
    const val ACTION_DISMISS = "ACTION_DISMISS"

    const val EXTRA_TITLE_RES_ID = "EXTRA_TITLE_RES_ID"
    const val EXTRA_CUSTOM_TITLE = "EXTRA_CUSTOM_TITLE"
  }

  private var wakeLock: PowerManager.WakeLock? = null
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private lateinit var coordinator: VoiceRecordingCoordinator

  private val notificationManager by lazy {
    getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
  }

  // 缓存 PendingIntent，避免每秒构建通知时进行 Binder IPC
  private var contentPendingIntent: PendingIntent? = null

  // 状态守卫：标记是否真正进入过录音态
  private var hasStartedRecording = false

  @StringRes private var currentTitleResId: Int = 0
  private var currentCustomTitle: String? = null

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
    coordinator = (application as ImLogApplication).recordingCoordinator

    contentPendingIntent =
      packageManager.getLaunchIntentForPackage(packageName)?.let {
        PendingIntent.getActivity(
            this,
            0,
            it,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
      }

    observeCoordinatorState()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_GUARD -> {
        // 核心加固：每次收到保护指令时重置守卫标记
        hasStartedRecording = false

        currentTitleResId = intent.getIntExtra(EXTRA_TITLE_RES_ID, 0)
        currentCustomTitle = intent.getStringExtra(EXTRA_CUSTOM_TITLE)

        acquireWakeLock()
        startForegroundInternal()
      }
      ACTION_DISMISS -> {
        stopForegroundAndFinish()
      }
    }
    return START_NOT_STICKY
  }

  private fun observeCoordinatorState() {
    serviceScope.launch {
      coordinator.recordingSnapshot
          .map { snapshot ->
            Pair(
                snapshot.status,
                snapshot.duration.inWholeSeconds,
            )
          }
          .distinctUntilChanged()
          .collect { (status, seconds) ->
            when (status) {
              RecordingSnapshot.Status.Recording -> {
                hasStartedRecording = true
                val formattedTime = seconds.secondsToAppMessageTimeFormat()
                val statusText =
                  getString(R.string.notification_recording_status_recording, formattedTime)
                updateNotification(statusText)
              }
              RecordingSnapshot.Status.Idle -> {
                // 只有当真正录制过之后变回 Idle，才退出，忽略启动初期的初始 Idle
                if (hasStartedRecording) {
                  stopForegroundAndFinish()
                }
              }
            }
          }
    }
  }

  private fun startForegroundInternal() {
    val initialText = getString(R.string.notification_recording_status_preparing)
    val notification = buildNotification(initialText)

    // minSdk >= 30，直接调用标准的 3 参数 startForeground 原生 API
    startForeground(
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
    )
  }

  private fun buildNotification(contentText: String): Notification {
    val resolvedTitle =
      when {
        !currentCustomTitle.isNullOrBlank() -> currentCustomTitle!!
        currentTitleResId != 0 -> getString(currentTitleResId)
        else -> getString(R.string.notification_recording_title_default)
      }

    return NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(resolvedTitle)
        .setContentText(contentText)
        .setSmallIcon(android.R.drawable.ic_btn_speak_now)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(contentPendingIntent)
        .build()
  }

  private fun updateNotification(contentText: String) {
    notificationManager.notify(NOTIFICATION_ID, buildNotification(contentText))
  }

  private fun createNotificationChannel() {
    val channel =
      NotificationChannel(
          CHANNEL_ID,
          getString(R.string.notification_channel_recording_name),
          NotificationManager.IMPORTANCE_LOW,
      )
          .apply {
            description = getString(R.string.notification_channel_recording_desc)
            setShowBadge(false)
          }
    notificationManager.createNotificationChannel(channel)
  }

  private fun acquireWakeLock() {
    if (wakeLock == null) {
      val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
      wakeLock =
        powerManager
            .newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "$APP_STATIC_NAME:VoiceRecordingLock",
            )
            .apply {
              setReferenceCounted(false)
              acquire(2 * 60 * 60 * 1000L) // 最大持有 2 小时安全超时
            }
    }
  }

  private fun releaseWakeLock() {
    wakeLock?.let {
      if (it.isHeld) {
        try {
          it.release()
        } catch (e: Exception) {
          Timber.w(e, "WakeLock release error")
        }
      }
    }
    wakeLock = null
  }

  private fun stopForegroundAndFinish() {
    releaseWakeLock()
    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  private fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", m, s)
  }

  override fun onDestroy() {
    super.onDestroy()
    releaseWakeLock()
    serviceScope.cancel()
  }

  override fun onBind(intent: Intent?): IBinder? = null
}