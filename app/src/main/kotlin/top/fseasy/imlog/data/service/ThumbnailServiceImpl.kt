package top.fseasy.imlog.data.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import coil3.imageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Precision
import coil3.size.Scale
import coil3.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.fseasy.imlog.data.mapper.toActualFileOrUri
import top.fseasy.imlog.domain.service.ImageThumbnailGenerateRequest
import top.fseasy.imlog.domain.model.AppImageFormat
import top.fseasy.imlog.domain.service.ThumbnailScale
import top.fseasy.imlog.domain.service.ThumbnailService
import top.fseasy.imlog.domain.service.VideoThumbnailGenerateRequest
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThumbnailServiceImpl @Inject constructor(
    @param:ApplicationContext val context: Context,
) : ThumbnailService {
    override suspend fun generateImageThumbnail(request: ImageThumbnailGenerateRequest): ByteArray =
        withContext(Dispatchers.IO) {
            val sourceFile = request.input.toActualFileOrUri(context)
            val requestBuilder = ImageRequest.Builder(context)
                .data(sourceFile)
                .precision(Precision.EXACT)     // EXACT，to ensure the least bytes
                .allowHardware(false)   //  false to allow we get bytes from memory (can't get from GPU)

            when (request.scale) {
                is ThumbnailScale.FitMaxSize -> {
                    val maxSize = request.scale.maxSize
                    requestBuilder
                        .size(maxSize, maxSize)
                        .scale(Scale.FIT)
                }

                is ThumbnailScale.FillByCroppingCenter -> {
                    requestBuilder
                        .size(request.scale.width, request.scale.height)
                        .scale(Scale.FILL) // Coil's Scale.FILL is Center Crop
                }
            }

            val imageLoader = context.imageLoader
            val result = imageLoader.execute(requestBuilder.build())
            if (result !is SuccessResult) {
                throw IOException("Coil failed to decode image: ${result.request.data}")
            }

            val bitmap = result.image.toBitmap()

            ByteArrayOutputStream().use { outputStream ->
                val success = bitmap.compress(
                    request.format.toBitmapCompressFormat(),
                    request.quality,
                    outputStream
                )
                if (!success) {
                    throw IOException("Bitmap compression failed")
                }
                outputStream.toByteArray()
            }
        }

    override suspend fun generateVideoThumbnail(request: VideoThumbnailGenerateRequest): ByteArray =
        withContext(Dispatchers.IO) {

        }

    /**
     * @throws IOException
     */
    private suspend fun processAndExecuteRequest(
        requestBuilder: ImageRequest.Builder,
        scale: ThumbnailScale,
        format: AppImageFormat,
        quality: Int,
    ): ByteArray {
        requestBuilder
            .precision(Precision.EXACT)     // EXACT，to ensure the least bytes
            .allowHardware(false)   // false to allow we get bytes from memory (can't get from GPU)

        when (scale) {
            is ThumbnailScale.FitMaxSize -> {
                val maxSize = scale.maxSize
                requestBuilder
                    .size(maxSize, maxSize)
                    .scale(Scale.FIT)
            }

            is ThumbnailScale.FillByCroppingCenter -> {
                requestBuilder
                    .size(scale.width, scale.height)
                    .scale(Scale.FILL) // Coil 的 Scale.FILL 即 Center Crop
            }
        }

        val imageLoader = context.imageLoader
        val result = imageLoader.execute(requestBuilder.build())
        if (result !is SuccessResult) {
            throw IOException("Coil failed to decode thumbnail: ${result.request.data}")
        }

        val bitmap = result.image.toBitmap()

        return ByteArrayOutputStream().use { outputStream ->
            val success = bitmap.compress(
                format.toBitmapCompressFormat(),
                quality,
                outputStream
            )
            if (!success) {
                throw IOException("Bitmap compression failed")
            }
            outputStream.toByteArray()
        }
    }
}

fun AppImageFormat.toBitmapCompressFormat() = when (this) {
    AppImageFormat.Webp -> Bitmap.CompressFormat.WEBP_LOSSY
    AppImageFormat.Jpeg -> Bitmap.CompressFormat.JPEG
    AppImageFormat.Png -> Bitmap.CompressFormat.PNG
}