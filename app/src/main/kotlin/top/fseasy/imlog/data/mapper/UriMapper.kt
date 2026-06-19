package top.fseasy.imlog.data.mapper

import android.net.Uri
import timber.log.Timber
import top.fseasy.imlog.domain.model.UriStr
import androidx.core.net.toUri

fun UriStr?.toUriOrNull(): Uri? {
    return runCatching {
        this?.value?.toUri()
    }.onFailure { e ->
        Timber.w(e, "Parse UriStr failed, uri=[${this?.value}]")
    }
        .getOrNull()
}

/**
 * @throws Exception
 */
fun UriStr.toUriOrThrow(): Uri = this.value.toUri()

fun Uri.toUriStr(): UriStr = UriStr(this.toString())