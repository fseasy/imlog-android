package top.fseasy.imlog.data.audio

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
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import top.fseasy.imlog.ImLogApplication
import top.fseasy.imlog.R
import top.fseasy.imlog.data.constants.APP_STATIC_NAME
import top.fseasy.imlog.domain.audio.RecordingSnapshot
import top.fseasy.imlog.domain.audio.VoiceRecordingCoordinator
import top.fseasy.imlog.domain.util.secondsToAppMessageTimeFormat

class VoiceRecordingService : Service() {

  companion object {
    const val CHANNEL_ID = "voice_recording_service_channel"
    const val NOTIFICATION_ID = 29327

    const val ACTION_GUARD = "ACTION_GUARD"
    const val ACTION_DISMISS = "ACTION_DISMISS"

    const val EXTRA_TITLE_RES_ID = "EXTRA_TITLE_RES_ID"
    const val EXTRA_CUSTOM_TITLE = "EXTRA_CUSTOM_TITLE"

    private const val MAX_WAKE_LOCK_MS = 2 * 60 * 60 * 1000L
    private val STARTUP_WATCHDOG_DELAY = 5.seconds
  }

  private var wakeLock: PowerManager.WakeLock? = null
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private lateinit var coordinator: VoiceRecordingCoordinator

  private val notificationManager by lazy {
    getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
  }

  // 缓存 PendingIntent，避免每秒刷新通知时重复执行 Binder IPC
  private var contentPendingIntent: PendingIntent? = null

  // 以下状态均严格在主线程（onStartCommand / Dispatchers.Main）读写
  private var hasStartedRecording = false
  private var isForegroundActive = false
  private var latestStartId = 0
  private var startupWatchdogJob: Job? = null

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
    latestStartId = startId

    when (intent?.action) {
      ACTION_GUARD -> {
        hasStartedRecording = coordinator.recordingSnapshot.value.status == RecordingSnapshot.Status.Recording
        currentTitleResId = intent.getIntExtra(EXTRA_TITLE_RES_ID, 0)
        currentCustomTitle = intent.getStringExtra(EXTRA_CUSTOM_TITLE)

        // 先尝试进入前台（防 Android 14+ 无录音权限时 startForeground 抛 SecurityException）
        if (startForegroundInternal()) {
          isForegroundActive = true
          acquireWakeLock()
          restartStartupWatchdog(targetStartId = startId)
        } else {
          stopForegroundAndFinish(targetStartId = startId)
        }
      }
      ACTION_DISMISS -> {
        stopForegroundAndFinish(targetStartId = startId)
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
                startupWatchdogJob?.cancel()
                startupWatchdogJob = null

                if (isForegroundActive) {
                  val formattedTime = seconds.secondsToAppMessageTimeFormat()
                  val statusText =
                      getString(R.string.notification_recording_status_recording, formattedTime)
                  updateNotification(statusText)
                }
              }
              RecordingSnapshot.Status.Idle -> {
                // 只有真正进入过录音后回到 Idle 才自动退出，忽略启动初期的初始 Idle
                if (hasStartedRecording) {
                  stopForegroundAndFinish(targetStartId = latestStartId)
                }
              }
            }
          }
    }
  }

  /**
   * 兜底看门狗：防止极端情况下（如启动后瞬间停止导致 StateFlow 合流跳过 Recording、 或启动失败且 App 已切后台导致 ACTION_DISMISS
   * 发送失败）Service 滞留 2 小时。
   */
  private fun restartStartupWatchdog(targetStartId: Int) {
    startupWatchdogJob?.cancel()
    startupWatchdogJob = serviceScope.launch {
      delay(STARTUP_WATCHDOG_DELAY)
      if (
          !hasStartedRecording &&
              coordinator.recordingSnapshot.value.status == RecordingSnapshot.Status.Idle
      ) {
        Timber.w("VoiceRecordingService startup watchdog triggered, stopping orphaned service")
        stopForegroundAndFinish(targetStartId = targetStartId)
      }
    }
  }

  private fun startForegroundInternal(): Boolean {
    val initialText = getString(R.string.notification_recording_status_preparing)
    val notification = buildNotification(initialText)

    return try {
      startForeground(
          NOTIFICATION_ID,
          notification,
          ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
      )
      true
    } catch (e: Throwable) {
      // 兜底 SecurityException（Android 14+ 缺少 RECORD_AUDIO 权限）及
      // ForegroundServiceStartNotAllowedException（切后台竞态）
      Timber.e(e, "Failed to startForeground in VoiceRecordingService")
      false
    }
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
              .apply { setReferenceCounted(false) }
    }
    // 每次 GUARD 都刷新超时上限，避免同实例二次复用时沿用旧计时
    wakeLock?.acquire(MAX_WAKE_LOCK_MS)
  }

  private fun releaseWakeLock() {
    wakeLock?.let {
      if (it.isHeld) {
        runCatching { it.release() }.onFailure { e -> Timber.w(e, "WakeLock release error") }
      }
    }
    wakeLock = null
  }

  private fun stopForegroundAndFinish(targetStartId: Int) {
    // 若已经有更新的 ACTION_GUARD 进来，不执行旧批次的停止逻辑
    if (targetStartId != latestStartId) return

    isForegroundActive = false
    startupWatchdogJob?.cancel()
    startupWatchdogJob = null
    releaseWakeLock()
    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelfResult(targetStartId)
  }

  override fun onDestroy() {
    super.onDestroy()
    isForegroundActive = false
    startupWatchdogJob?.cancel()
    startupWatchdogJob = null
    releaseWakeLock()
    serviceScope.cancel()
  }

  override fun onBind(intent: Intent?): IBinder? = null
}
