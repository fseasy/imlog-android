package top.fseasy.imlog.ui.util

import android.content.res.Configuration
import top.fseasy.imlog.domain.model.ScreenOrientation

fun Configuration.toScreenConfiguration() =
    if (this.orientation == Configuration.ORIENTATION_LANDSCAPE) {
      ScreenOrientation.Landscape
    } else {
      ScreenOrientation.Portrait // All the other values, use the portrait
    }
