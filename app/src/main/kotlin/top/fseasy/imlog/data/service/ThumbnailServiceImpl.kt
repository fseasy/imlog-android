package top.fseasy.imlog.data.service

import android.R
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import androidx.annotation.OptIn
import androidx.concurrent.futures.await
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.inspector.frame.FrameExtractor
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
import top.fseasy.imlog.data.mapper.toUri
import top.fseasy.imlog.domain.service.ImageThumbnailGenerateRequest
import top.fseasy.imlog.domain.model.AppImageFormat
import top.fseasy.imlog.domain.service.ThumbnailScale
import top.fseasy.imlog.domain.service.ThumbnailService
import top.fseasy.imlog.domain.service.VideoThumbnailGenerateRequest
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.text.compareTo

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

            bitmapToImageBytes(
                result.image.toBitmap(),
                quality = request.quality,
                format = request.format
            )
        }

    @OptIn(UnstableApi::class)
    override suspend fun generateVideoThumbnail(request: VideoThumbnailGenerateRequest): ByteArray =
        withContext(Dispatchers.IO) {
            val mediaItem = MediaItem.fromUri(request.input.toUri(context))
            val presentation = calculateVideoFramePresentation(
                inputWidth = request.inputWidth,
                inputHeight = request.inputHeight,
                scale = request.scale
            )

            FrameExtractor.Builder(context, mediaItem)
                .setEffects(listOf(presentation))
                .build()
                .use { extractor ->
                    val thumbnail = extractor.thumbnail.await()
                    bitmapToImageBytes(
                        thumbnail.bitmap,
                        quality = request.quality,
                        format = request.format
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

    @OptIn(UnstableApi::class)
    private fun calculateVideoFramePresentation(
        inputWidth: Int,
        inputHeight: Int,
        scale: ThumbnailScale,
    ): Presentation {
        if (inputWidth <= 0 || inputHeight <= 0) {
            throw IllegalArgumentException("Input w=$inputWidth, h=$inputHeight are not valid")
        }

        fun toEvenInt(i: Int): Int {
            return if (i % 2 == 0) i else i - 1
        }

        fun toEvenInt(d: Double): Int {
            val value = d.roundToInt()
            return toEvenInt(value)
        }

        val wDouble = inputWidth.toDouble()
        val hDouble = inputHeight.toDouble()

        return when (scale) {
            is ThumbnailScale.FitMaxSize -> {
                // w x h => fit in maxSize x maxSize, need to scale the bigger one to the maxSize
                //          if bigger one >= maxSize
                val (targetWidthDouble, targetHeightDouble) = if (hDouble >= wDouble) {
                    // height is the bigger one, use it as the max
                    val h = min(hDouble, scale.maxSize.toDouble())
                    val w = wDouble / hDouble * h
                    w to h
                } else {
                    val w = min(wDouble, scale.maxSize.toDouble())
                    val h = hDouble / wDouble * w
                    w to h
                }
                Presentation.createForWidthAndHeight(
                    toEvenInt(targetWidthDouble),
                    toEvenInt(targetHeightDouble),
                    // any layout is OK. use CROP to avoid stretch/padding under math precision edge issue
                    Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
                )
            }

            is ThumbnailScale.FillByCroppingCenter -> {
                Presentation.createForWidthAndHeight(
                    toEvenInt(scale.width),
                    toEvenInt(scale.height),
                    Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
                )
            }
        }
    }
}

fun AppImageFormat.toBitmapCompressFormat() = when (this) {
    AppImageFormat.Webp -> Bitmap.CompressFormat.WEBP_LOSSY
    AppImageFormat.Jpeg -> Bitmap.CompressFormat.JPEG
    AppImageFormat.Png -> Bitmap.CompressFormat.PNG
}
