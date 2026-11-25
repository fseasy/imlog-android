package top.fseasy.imlog.features.home.conversation

import android.content.res.Resources
import androidx.annotation.StringRes
import top.fseasy.imlog.R

fun GeneralNotice.toMessage(resources: Resources): String {
  return resources.getString(toResId())
}

@StringRes
fun GeneralNotice.toResId(): Int {
  return when (this) {
    GeneralNotice.FailedToResolveFile -> R.string.term_failed_to_resolve_file
  }
}
