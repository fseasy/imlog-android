package top.fseasy.imlog

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import top.fseasy.imlog.util.CrashReportingTree

@HiltAndroidApp
class ImLogApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(CrashReportingTree())
        }
        SingletonImageLoader.setSafe {
            ImageLoader.Builder(this)
                .crossfade(true)
                .build()
        }
    }
}
