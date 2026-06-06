package top.fseasy.imlog.util

import android.annotation.SuppressLint
import android.util.Log
import timber.log.Timber

class CrashReportingTree : Timber.Tree() {
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


inline fun <T> T?.logIfNull(message: () -> String): T? {
    if (this == null) {
        Timber.d(message())
    }
    return this
}