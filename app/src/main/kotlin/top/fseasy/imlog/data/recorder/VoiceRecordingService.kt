package top.fseasy.imlog.data.recorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
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

class VoiceRecordingService : Service() {

  companion object {
    const val CHANNEL_ID = "voice_recording_service_channel"
    const val NOTIFICATION_ID = 9527

    // Action 定义
    const val ACTION_GUARD = "ACTION_GUARD" // 启动并保活
    const val ACTION_DISMISS = "ACTION_DISMISS" // 退出并清除保活
    const val ACTION_PAUSE = "ACTION_PAUSE" // 通知栏点击：暂停
    const val ACTION_RESUME = "ACTION_RESUME" // 通知栏点击：继续
    const val ACTION_STOP = "ACTION_STOP" // 通知栏点击：完成

    // 业务场景文案配置 Extras
    const val EXTRA_TITLE_RES_ID = "EXTRA_TITLE_RES_ID" // 传入 @StringRes 资源 ID (推荐，支持自动切换多语言)
    const val EXTRA_CUSTOM_TITLE = "EXTRA_CUSTOM_TITLE" // 传入纯文本动态标题 (如“会议：第四季度汇报”)
  }

  private var wakeLock: PowerManager.WakeLock? = null
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private lateinit var coordinator: VoiceRecordingCoordinator

  // 缓存当前场景的标题配置
  @StringRes private var currentTitleResId: Int = 0
  private var currentCustomTitle: String? = null

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
    coordinator = (application as ImLogApplication).recordingCoordinator
    observeCoordinatorState()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_GUARD -> {
        // 解析传递过来的场景标题
        currentTitleResId = intent.getIntExtra(EXTRA_TITLE_RES_ID, 0)
        currentCustomTitle = intent.getStringExtra(EXTRA_CUSTOM_TITLE)

        acquireWakeLock()
        startForegroundInternal()
      }
      ACTION_PAUSE -> {
        serviceScope.launch { coordinator.pause() }
      }
      ACTION_RESUME -> {
        serviceScope.launch { coordinator.resume() }
      }
      ACTION_STOP -> {
        serviceScope.launch {
          coordinator.stopLockedAndSend()
          stopForegroundAndFinish()
        }
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
            Triple(
                snapshot.status,
                snapshot.duration.inWholeSeconds,
                snapshot.status == RecordingSnapshot.Status.Paused,
            )
          }
          .distinctUntilChanged { old, new ->
            old.first == new.first && old.second == new.second
          }
          .collect { (status, seconds, _) ->
            when (status) {
              RecordingSnapshot.Status.Recording -> {
                val formattedTime = formatDuration(seconds)
                val statusText =
                    getString(R.string.notification_recording_status_recording, formattedTime)
                updateNotification(statusText, isPaused = false)
              }
              RecordingSnapshot.Status.Paused -> {
                val formattedTime = formatDuration(seconds)
                val statusText =
                    getString(R.string.notification_recording_status_paused, formattedTime)
                updateNotification(statusText, isPaused = true)
              }
              RecordingSnapshot.Status.Stopped,
              RecordingSnapshot.Status.Idle -> {
                stopForegroundAndFinish()
              }
            }
          }
    }
  }

  private fun startForegroundInternal() {
    val initialText = getString(R.string.notification_recording_status_preparing)
    val notification = buildNotification(initialText, isPaused = false)
    startForeground(
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
    )
  }

  private fun buildNotification(contentText: String, isPaused: Boolean): Notification {
    val contentPendingIntent =
        packageManager.getLaunchIntentForPackage(packageName)?.let {
          PendingIntent.getActivity(
              this,
              0,
              it,
              PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
          )
        }

    val toggleActionIntent =
        Intent(this, VoiceRecordingService::class.java).apply {
          action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        }
    val togglePendingIntent =
        PendingIntent.getService(
            this,
            1,
            toggleActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    val stopActionIntent =
        Intent(this, VoiceRecordingService::class.java).apply {
          action = ACTION_STOP
        }
    val stopPendingIntent =
        PendingIntent.getService(
            this,
            2,
            stopActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    // 动态解析标题：自定义字符串 > 资源 ID 多语言 > 默认多语言标题
    val resolvedTitle =
        when {
          !currentCustomTitle.isNullOrBlank() -> currentCustomTitle!!
          currentTitleResId != 0 -> getString(currentTitleResId)
          else -> getString(R.string.notification_recording_title_default)
        }

    // 动态多语言 Action 按钮文案
    val toggleActionTitle =
        if (isPaused) {
          getString(R.string.notification_action_resume)
        } else {
          getString(R.string.notification_action_pause)
        }
    val finishActionTitle = getString(R.string.notification_action_finish)

    return NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(resolvedTitle)
        .setContentText(contentText)
        .setSmallIcon(android.R.drawable.ic_btn_speak_now)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(contentPendingIntent)
        .addAction(
            if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
            toggleActionTitle,
            togglePendingIntent,
        )
        .addAction(android.R.drawable.checkbox_on_background, finishActionTitle, stopPendingIntent)
        .build()
  }

  private fun updateNotification(contentText: String, isPaused: Boolean) {
    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.notify(NOTIFICATION_ID, buildNotification(contentText, isPaused))
  }

  private fun createNotificationChannel() {
    val channel =
        NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_recording_name), // 多语言渠道名
                NotificationManager.IMPORTANCE_LOW,
            )
            .apply {
              description = getString(R.string.notification_channel_recording_desc) // 多语言渠道描述
              setShowBadge(false)
            }
    val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    manager.createNotificationChannel(channel)
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
                acquire(2 * 60 * 60 * 1000L) // max 2 hours
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
