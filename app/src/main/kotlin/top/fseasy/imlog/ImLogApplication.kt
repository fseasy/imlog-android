package top.fseasy.imlog

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject
import timber.log.Timber
import top.fseasy.imlog.data.audio.AndroidAudioRecorder
import top.fseasy.imlog.data.audio.AndroidRecordingGuardian
import top.fseasy.imlog.data.util.CrashReportingTree
import top.fseasy.imlog.domain.audio.VoiceRecordingCoordinator

@HiltAndroidApp
class ImLogApplication : Application(), Configuration.Provider {
  // For HiltWorker
  @Inject lateinit var workerFactory: HiltWorkerFactory

  override val workManagerConfiguration: Configuration
    get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

  // 全局常驻协程作用域（应用被杀死才销毁）. For Future KMP structure
  val applicationLevelDefaultScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  // 全局唯一的录音会话协调者
  val recordingCoordinator: VoiceRecordingCoordinator by lazy {
    VoiceRecordingCoordinator(
        // 关键点：传入 applicationScope，切不可传 viewModelScope！
        recorder = AndroidAudioRecorder(this, applicationLevelDefaultScope),
        guardian = AndroidRecordingGuardian(this)
    )
  }

  override fun onCreate() {
    super.onCreate()
    if (BuildConfig.DEBUG) {
      Timber.plant(Timber.DebugTree())
    } else {
      Timber.plant(CrashReportingTree())
    }
    SingletonImageLoader.setSafe {
      ImageLoader.Builder(this).crossfade(true).build()
    }
  }


}
