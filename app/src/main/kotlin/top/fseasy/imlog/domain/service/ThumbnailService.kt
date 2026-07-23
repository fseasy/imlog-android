package top.fseasy.imlog.domain.service

import top.fseasy.imlog.domain.model.AbsolutePathModel
import top.fseasy.imlog.domain.model.AppImageFormat
import kotlin.math.roundToInt

sealed interface ThumbnailScale {
    fun calculateScaledWidthHeight(inputWidth: Int, inputHeight: Int): Pair<Int, Int>

    /**
     * Obey the input image's aspect and fit to the max-sized boundary without padding.
     * Do nothing if image's size already fit to the max-size
     */
    data class ScaleToFit(val maxWidth: Int, val maxHeight: Int) : ThumbnailScale {
        init {
            require(maxWidth > 0) { "maxWidth must > 0, given $maxWidth" }
            require(maxHeight > 0) { "maxHeight must > 0, given $maxHeight" }
        }

        /**
         * Scale the relative-longer one to the requested max-size, without stretching.
         */
        override fun calculateScaledWidthHeight(inputWidth: Int, inputHeight: Int): Pair<Int, Int> {
            if (inputWidth <= 0 || inputHeight <= 0) {
                throw IllegalArgumentException("Input w=$inputWidth, h=$inputHeight are not valid")
            }
            val wDouble = inputWidth.toDouble()
            val hDouble = inputHeight.toDouble()

            val scaleW = maxWidth.toDouble() / wDouble
            val scaleH = maxHeight.toDouble() / hDouble
            // Avoid stretch if already smaller than max size
            val scale = minOf(1.0, scaleW, scaleH)

            val targetWidthDouble = wDouble * scale
            val targetHeightDouble = hDouble * scale

            return toEvenInt(targetWidthDouble) to toEvenInt(targetHeightDouble)
        }
    }

    /**
     * Fill to the requested size by scale and cropping the image center.
     */
    data class ScaleAndCroppingCenterToFill(val width: Int, val height: Int) : ThumbnailScale {
        init {
            require(width > 0) { "width must > 0, given $width" }
            require(height > 0) { "height must > 0, given $height" }
        }

        /**
         * If any edge length of original image less than target size, we give up stretching
         * by scale the target size.
         *
         * else, the target size should be just kept the set value, leave the cropping logic
         * do the left things.
         */
        override fun calculateScaledWidthHeight(inputWidth: Int, inputHeight: Int): Pair<Int, Int> {
            if (inputWidth <= 0 || inputHeight <= 0) {
                throw IllegalArgumentException("Input w=$inputWidth, h=$inputHeight are not valid")
            }
            val targetWidthDouble = width.toDouble()
            val targetHeightDouble = height.toDouble()

            val scaleW = inputWidth / targetWidthDouble
            val scaleH = inputHeight / targetHeightDouble
            // if scale all bigger than 1, use 1 => no need to change the target size
            // else choose the smaller one to avoid stretching the image
            val cropScale = minOf(1.0, scaleW, scaleH)

            // 计算最终输出的尺寸（保持目标比例，且不超出原图尺寸）
            val widthDouble = targetWidthDouble * cropScale
            val heightDouble = targetHeightDouble * cropScale

            return toEvenInt(widthDouble) to toEvenInt(heightDouble)
        }
    }
}

data class ThumbnailGenerateRequest(
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
    suspend fun generateImageThumbnail(request: ThumbnailGenerateRequest): ByteArray

    /**
     * @throws Exception won't catch any exception.
     */
    suspend fun generateVideoThumbnail(request: ThumbnailGenerateRequest): ByteArray
}

private fun toEvenInt(i: Int): Int {
    return if (i % 2 == 0) i else i - 1
}

private fun toEvenInt(d: Double): Int {
    val value = d.roundToInt()
    return toEvenInt(value)
}
