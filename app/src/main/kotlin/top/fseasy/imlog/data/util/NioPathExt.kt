package top.fseasy.imlog.data.util

import android.os.Parcel
import java.nio.file.Path
import java.nio.file.Paths
import kotlinx.parcelize.Parceler

/** To Support rememberParcelable */
object NioPathParceler : Parceler<Path?> {
  override fun create(parcel: Parcel): Path? {
    val pathString = parcel.readString() ?: return null
    return Paths.get(pathString)
  }

  override fun Path?.write(parcel: Parcel, flags: Int) {
    parcel.writeString(this?.toString())
  }
}
