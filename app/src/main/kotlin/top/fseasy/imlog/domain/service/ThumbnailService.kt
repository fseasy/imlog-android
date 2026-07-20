package top.fseasy.imlog.domain.service

import top.fseasy.imlog.domain.model.AbsolutePathModel

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

enum class ThumbnailFormat {
    Webp, Jpeg, Png;
}

data class ImageThumbnailGenerateRequest(
    val input: AbsolutePathModel,
    val scale: ThumbnailScale,
    val quality: Int = 75, // Value between 0 - 100
    val format: ThumbnailFormat,
)

interface ThumbnailService {
    /**
     * @throws Exception won't catch any exception.
     */
    suspend fun generateImageThumbnail(
        request: ImageThumbnailGenerateRequest,
    ): ByteArray

}