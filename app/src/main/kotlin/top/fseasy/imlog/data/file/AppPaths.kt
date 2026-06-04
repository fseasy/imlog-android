package top.fseasy.imlog.data.file

import android.content.Context

// data/AppPaths.kt
object AppPaths {
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val messageRootDir: String get() = appContext.filesDir.absolutePath
}