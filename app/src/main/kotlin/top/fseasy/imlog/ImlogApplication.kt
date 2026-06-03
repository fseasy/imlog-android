package top.fseasy.imlog

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import top.fseasy.imlog.data.paths.AppPaths

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

private class CrashReportingTree : Timber.Tree() {
    @SuppressLint("LogNotTimber")
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority >= Log.WARN) {
            if (t != null) {
                // TODO
                // FirebaseCrashlytics.getInstance().recordException(t)
                Log.e(tag ?: "CrashReporting", message, t)
            } else {
                // TODO
                // FirebaseCrashlytics.getInstance().log("[$tag] $message")
                Log.println(priority, tag ?: "CrashReporting", message)
            }
        }
    }
}
