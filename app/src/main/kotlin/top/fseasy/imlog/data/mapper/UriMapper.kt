package top.fseasy.imlog.data.mapper

import android.net.Uri
import android.os.Parcel
import androidx.core.net.toUri
import kotlinx.parcelize.Parceler
import top.fseasy.imlog.domain.model.UriStr

fun UriStr?.toUriOrNull() = this?.value?.toUri()

/** it won't throw exceptions! as it didn't verify the string */
fun UriStr.toUri(): Uri = this.value.toUri()

fun Uri.toUriStr(): UriStr = UriStr(this.toString())

object UriStrParceler : Parceler<UriStr> {
  override fun create(parcel: Parcel): UriStr = UriStr(parcel.readString()!!)

  override fun UriStr.write(parcel: Parcel, flags: Int) = parcel.writeString(this.value)
}
