package top.fseasy.imlog

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import top.fseasy.imlog.util.CrashReportingTree

@HiltAndroidApp
class ImlogApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(CrashReportingTree())
        }
        AppPaths.init(this)
    }
}
