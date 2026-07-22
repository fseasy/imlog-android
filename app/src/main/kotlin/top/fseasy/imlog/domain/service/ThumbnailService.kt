package top.fseasy.imlog.domain.service

import top.fseasy.imlog.domain.model.AbsolutePathModel
import top.fseasy.imlog.domain.model.AppImageFormat
import kotlin.time.Duration

sealed interface ThumbnailScale {
    /**
     * Obey the input image's aspect and fit to the max-size boundary.
     */
    data class FitMaxSize(val maxSize: Int) : ThumbnailScale {
        init {
            require(maxSize > 0) { "MaxSize must > 0, given $maxSize" }
        }
    }

    /**
     * Fill to the requested size by cropping the image center.
     */
    data class FillByCroppingCenter(val width: Int, val height: Int) : ThumbnailScale
}

data class ImageThumbnailGenerateRequest(
    val input: AbsolutePathModel,
    val scale: ThumbnailScale,
    val quality: Int = 75, // Value between 0 - 100
    val format: AppImageFormat,
)

data class VideoThumbnailGenerateRequest(
    val input: AbsolutePathModel,
    val inputWidth: Int,
    val inputHeight: Int,
    val scale: ThumbnailScale,
    val quality: Int = 75, // Value between 0 - 100
    val format: AppImageFormat,
)

interface ThumbnailService {
    /**
     * @throws Exception won't catch any exception.
     */
    suspend fun generateImageThumbnail(request: ImageThumbnailGenerateRequest): ByteArray

    /**
     * @throws Exception won't catch any exception.
     */
    suspend fun generateVideoThumbnail(request: VideoThumbnailGenerateRequest): ByteArray
}