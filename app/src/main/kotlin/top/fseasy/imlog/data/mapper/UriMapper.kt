package top.fseasy.imlog.data.mapper

import android.net.Uri
import android.os.Parcel
import androidx.core.net.toUri
import kotlinx.parcelize.Parceler
import timber.log.Timber
import top.fseasy.imlog.domain.model.UriStr

fun UriStr?.toUriOrNull(): Uri? {
  return runCatching {
    this?.value?.toUri()
  }
      .onFailure { e ->
        Timber.w(e, "Parse UriStr failed, uri=[${this?.value}]")
      }
      .getOrNull()
}

/** @throws Exception */
fun UriStr.toUriOrThrow(): Uri = this.value.toUri()

fun Uri.toUriStr(): UriStr = UriStr(this.toString())

object UriStrParceler : Parceler<UriStr> {
  override fun create(parcel: Parcel): UriStr = UriStr(parcel.readString()!!)

  override fun UriStr.write(parcel: Parcel, flags: Int) = parcel.writeString(this.value)
}
