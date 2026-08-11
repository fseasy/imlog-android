package top.fseasy.imlog.data.mapper

import android.os.Parcel
import java.io.File
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlinx.parcelize.Parceler
import top.fseasy.imlog.domain.model.AbsolutePathModel
import top.fseasy.imlog.domain.model.AppPath

/** To java.io.File */
fun AppPath.toFile() = File(value)

/**
 * To nio path
 *
 * @throws java.nio.file.InvalidPathException if the string can't be converted to Path.
 */
fun AppPath.toNioPath() = Paths.get(value)

fun File.toAppPath() = AppPath(absolutePath)

fun java.nio.file.Path.toAppPath() = AppPath(absolutePathString())

object AppPathParceler : Parceler<AppPath> {
  override fun create(parcel: Parcel): AppPath = AppPath(parcel.readString()!!)

  override fun AppPath.write(parcel: Parcel, flags: Int) = parcel.writeString(this.value)
}

object AbsolutePathModelParceler : Parceler<AbsolutePathModel?> {

  private const val TYPE_NULL = 0
  private const val TYPE_URI_STR = 1
  private const val TYPE_APP_PATH = 2

  override fun create(parcel: Parcel): AbsolutePathModel? {
    val type = parcel.readInt()
    if (type == TYPE_NULL) return null

    return when (type) {
      TYPE_URI_STR -> AbsolutePathModel.UriStrModel(UriStrParceler.create(parcel))
      TYPE_APP_PATH -> AbsolutePathModel.AppPathModel(AppPathParceler.create(parcel))
      else -> null
    }
  }

  override fun AbsolutePathModel?.write(parcel: Parcel, flags: Int) {
    when (this) {
      null -> parcel.writeInt(TYPE_NULL)
      is AbsolutePathModel.UriStrModel -> {
        parcel.writeInt(TYPE_URI_STR)
        with(UriStrParceler) { value.write(parcel, flags) }
      }
      is AbsolutePathModel.AppPathModel -> {
        parcel.writeInt(TYPE_APP_PATH)
        with(AppPathParceler) { value.write(parcel, flags) }
      }
    }
  }
}
