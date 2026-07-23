package top.fseasy.imlog.data.service

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.concurrent.futures.await
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.inspector.frame.FrameExtractor
import coil3.imageLoader
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
import top.fseasy.imlog.data.mapper.toBitmapCompressFormat
import top.fseasy.imlog.data.mapper.toUri
import top.fseasy.imlog.domain.model.AppImageFormat
import top.fseasy.imlog.domain.service.ThumbnailGenerateRequest
import top.fseasy.imlog.domain.service.ThumbnailService
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThumbnailServiceImpl @Inject constructor(
    @param:ApplicationContext val context: Context,
) : ThumbnailService {
    override suspend fun generateImageThumbnail(request: ThumbnailGenerateRequest): ByteArray =
        withContext(Dispatchers.IO) {
            val sourceFile = request.input.toActualFileOrUri(context)
            val (targetW, targetH) = request.scale.calculateScaledWidthHeight(
                request.inputWidth,
                request.inputHeight
            )
            val requestBuilder = ImageRequest.Builder(context)
                .data(sourceFile)
                .allowHardware(false)   //  false to allow we get bytes from memory (can't get from GPU)
                .precision(Precision.EXACT)  // Exact to avoid any fuzzy condition
                .scale(Scale.FILL) // Set to FILL is applicable to both Fit & Crop with our pre-calculated size
                .size(targetW, targetH)

            val imageLoader = context.imageLoader
            val result = imageLoader.execute(requestBuilder.build())
            if (result !is SuccessResult) {
                throw IOException("Coil failed to decode image: ${result.request.data}")
            }

            bitmapToImageBytes(
                result.image.toBitmap(), quality = request.quality, format = request.format
            )
        }

    @OptIn(UnstableApi::class)
    override suspend fun generateVideoThumbnail(request: ThumbnailGenerateRequest): ByteArray =
        withContext(Dispatchers.IO) {
            val mediaItem = MediaItem.fromUri(request.input.toUri(context))
            val (targetW, targetH) = request.scale.calculateScaledWidthHeight(
                request.inputWidth,
                request.inputHeight
            )
            // CROP suits for both 2 scale condition under our calculated size
            val presentation = Presentation.createForWidthAndHeight(
                targetW,
                targetH,
                Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
            )

            FrameExtractor.Builder(context, mediaItem)
                .setEffects(listOf(presentation))
                .build()
                .use { extractor ->
                    val thumbnail = extractor.thumbnail.await()
                    bitmapToImageBytes(
                        thumbnail.bitmap, quality = request.quality, format = request.format
                    )
                }
        }

    /**
     * @throws IOException
     */
    private suspend fun bitmapToImageBytes(
        bitmap: Bitmap,
        quality: Int,
        format: AppImageFormat,
    ): ByteArray = withContext(Dispatchers.Default) {
        ByteArrayOutputStream().use { outputStream ->
            val success = bitmap.compress(
                format.toBitmapCompressFormat(), quality, outputStream
            )
            if (!success) {
                throw IOException("Bitmap compression failed")
            }
            outputStream.toByteArray()
        }
    }

}
