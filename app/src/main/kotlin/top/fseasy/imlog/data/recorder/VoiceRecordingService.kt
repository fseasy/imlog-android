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
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import top.fseasy.imlog.ImLogApplication
import top.fseasy.imlog.domain.recorder.RecordingSnapshot
import top.fseasy.imlog.domain.recorder.VoiceRecordingCoordinator

class VoiceRecordingService : Service() {

  companion object {
    const val CHANNEL_ID = "voice_recording_service_channel"
    const val NOTIFICATION_ID = 9527

    // 完整的 Action 意图定义
    const val ACTION_GUARD = "ACTION_GUARD" // 启动并常驻保活
    const val ACTION_DISMISS = "ACTION_DISMISS" // 退出并清除保活
    const val ACTION_PAUSE = "ACTION_PAUSE" // 通知栏点击：暂停
    const val ACTION_RESUME = "ACTION_RESUME" // 通知栏点击：继续
    const val ACTION_STOP = "ACTION_STOP" // 通知栏点击：完成
  }

  private var wakeLock: PowerManager.WakeLock? = null
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

  // 全局唯一的协调器
  private lateinit var coordinator: VoiceRecordingCoordinator

  override fun onCreate() {
    super.onCreate()
    // 1. 优先创建通知渠道（Android 8.0+ 必须第一步执行）
    createNotificationChannel()

    // 2. 获取全局 Coordinator 单例
    coordinator = (application as ImLogApplication).recordingCoordinator

    // 3. 启动降频监听流
    observeCoordinatorState()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_GUARD -> {
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
          // 通知栏点击“完成”：停止录制并退出服务
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

  /** 核心优化：整秒级降频监听 将 100ms 高频脉冲降频为 1s 一次，只在“状态改变”或“秒数增加”时更新通知栏 */
  private fun observeCoordinatorState() {
    serviceScope.launch {
      coordinator.recordingSnapshot
          .map { snapshot ->
            // 将原始数据提取为轻量三元组：状态、整秒数、是否暂停
            Triple(
                snapshot.status,
                snapshot.duration.inWholeSeconds,
                snapshot.status == RecordingSnapshot.Status.Paused,
            )
          }
          .distinctUntilChanged { old, new ->
            // 核心去重：只有状态变化，或者整秒数字变化时才允许往下游发射
            old.first == new.first && old.second == new.second
          }
          .collect { (status, seconds, isPaused) ->
            when (status) {
              RecordingSnapshot.Status.Recording -> {
                updateNotification("正在录音: ${formatDuration(seconds)}", isPaused = false)
              }
              RecordingSnapshot.Status.Paused -> {
                updateNotification("已暂停: ${formatDuration(seconds)}", isPaused = true)
              }
              RecordingSnapshot.Status.Stopped,
              RecordingSnapshot.Status.Idle -> {
                // 如果外部或内部已停止，前台服务自行退场
                stopForegroundAndFinish()
              }
            }
          }
    }
  }

  private fun startForegroundInternal() {
    val notification = buildNotification("正在准备录制...", isPaused = false)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(
          NOTIFICATION_ID,
          notification,
          ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
      )
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
  }

  private fun buildNotification(contentText: String, isPaused: Boolean): Notification {
    // 1. 点击通知卡片主体：回退唤起 App 主界面
    val contentPendingIntent =
        packageManager.getLaunchIntentForPackage(packageName)?.let {
          PendingIntent.getActivity(
              this,
              0,
              it,
              PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
          )
        }

    // 2. 暂停/继续 PendingIntent
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

    // 3. 完成/停止 PendingIntent
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

    return NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("语音笔记正在录制")
        .setContentText(contentText)
        .setSmallIcon(android.R.drawable.ic_btn_speak_now)
        .setOngoing(true)
        .setOnlyAlertOnce(true) // 避免每次更新都震动或发出提示音
        .setContentIntent(contentPendingIntent) // 点击打开 App
        .addAction(
            if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
            if (isPaused) "继续" else "暂停",
            togglePendingIntent,
        )
        .addAction(android.R.drawable.checkbox_on_background, "完成", stopPendingIntent)
        .build()
  }

  private fun updateNotification(contentText: String, isPaused: Boolean) {
    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.notify(NOTIFICATION_ID, buildNotification(contentText, isPaused))
  }

  private fun acquireWakeLock() {
    if (wakeLock == null) {
      val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
      wakeLock =
          powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NoteApp:AudioLock").apply {
            setReferenceCounted(false)
            acquire(120 * 60 * 1000L /* 2小时最长超时，防死锁 */)
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
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      stopForeground(STOP_FOREGROUND_REMOVE)
    } else {
      @Suppress("DEPRECATION") stopForeground(true)
    }
    stopSelf()
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel =
          NotificationChannel(
                  CHANNEL_ID,
                  "语音录制通知",
                  NotificationManager.IMPORTANCE_LOW, // 低优先级：静默不弹横幅打扰用户
              )
              .apply {
                description = "展示当前语音录制进度"
                setShowBadge(false)
              }
      val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      manager.createNotificationChannel(channel)
    }
  }

  private fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
  }

  override fun onDestroy() {
    super.onDestroy()
    releaseWakeLock()
    serviceScope.cancel()
  }

  override fun onBind(intent: Intent?): IBinder? = null
}
