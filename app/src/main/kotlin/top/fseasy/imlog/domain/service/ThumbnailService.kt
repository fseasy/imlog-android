package top.fseasy.imlog.domain.service

import top.fseasy.imlog.domain.model.AbsolutePathModel
import top.fseasy.imlog.domain.model.AppImageFormat
import kotlin.time.Duration

sealed interface ThumbnailScale {
    /**
     * Obey the input image's aspect and fit to the max-size boundary.
     */
    data class FitMaxSize(val maxSize: Int) : ThumbnailScale

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
    val videoDuration: Duration, // Used to quickly decide the key frame position
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