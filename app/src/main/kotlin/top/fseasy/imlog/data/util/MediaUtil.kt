package top.fseasy.imlog.data.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.drawable.toBitmapOrNull
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import java.io.File

sealed interface GenerateThumbnailResult {
    data object Success : GenerateThumbnailResult
    data class Error(val cause: Throwable) : GenerateThumbnailResult
}

suspend fun generateAndSaveThumbnail(
    context: Context,
    uri: Uri,
    targetFile: File,
    maxWidth: Int = 300,
    maxHeight: Int = 300,
): GenerateThumbnailResult {
    // 使用 Coil 的 ImageLoader 直接从 Uri 获取 Bitmap
    val request = ImageRequest.Builder(context)
        .data(uri)
        .size(maxWidth, maxHeight) // 强制缩放，减小内存占用
        .allowHardware(false) // 必须设为 false，因为后续要 compress, GPU 里的不能 compress
        .build()

    val bitmap = when (val result = context.imageLoader.execute(request)) {
        is ErrorResult -> return GenerateThumbnailResult.Error(result.throwable)
        is SuccessResult -> result.image.asDrawable(context.resources)
            .toBitmapOrNull()
    }
    // 保存到本地
    return bitmap?.let {
        targetFile.outputStream()
            .use { stream ->
                it.compress(Bitmap.CompressFormat.WEBP_LOSSY, 75, stream)
            }
        GenerateThumbnailResult.Success
    } ?: GenerateThumbnailResult.Error(IllegalStateException("bitmap generate get null"))
}

fun isDimensionValid(w: Int?, h: Int?) = w != null && w > 0 && h != null && h > 0