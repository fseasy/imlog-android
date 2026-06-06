package top.fseasy.imlog.util

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import java.io.File


suspend fun generateAndSaveThumbnail(context: Context, uri: Uri, targetFile: File) {
    // 使用 Coil 的 ImageLoader 直接从 Uri 获取 Bitmap
    val request = ImageRequest.Builder(context)
        .data(uri)
        .size(300, 300) // 强制缩放，减小内存占用
        .allowHardware(false) // 必须设为 false，因为要存入本地磁盘
        .build()

    val result = context.imageLoader.execute(request).drawable
    val bitmap = (result as? BitmapDrawable)?.bitmap

    // 保存到本地
    bitmap?.let {
        targetFile.outputStream()
            .use { stream ->
                it.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            }
    }
}