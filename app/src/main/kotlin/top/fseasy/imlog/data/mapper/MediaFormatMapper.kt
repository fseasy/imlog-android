package top.fseasy.imlog.data.mapper

import android.graphics.Bitmap
import top.fseasy.imlog.domain.model.AppImageFormat

fun AppImageFormat.toBitmapCompressFormat() = when (this) {
    AppImageFormat.Webp -> Bitmap.CompressFormat.WEBP_LOSSY
    AppImageFormat.Jpeg -> Bitmap.CompressFormat.JPEG
    AppImageFormat.Png -> Bitmap.CompressFormat.PNG
}
