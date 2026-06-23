package top.fseasy.imlog.ui.util

import android.content.Context
import top.fseasy.imlog.R

fun Throwable.toDisplayMessage(context: Context) =
    this.message ?: context.getString(R.string.error_unknown)