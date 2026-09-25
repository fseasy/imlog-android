package top.fseasy.imlog.features.home.topiclog.timeline.messagebubble

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import timber.log.Timber
import top.fseasy.imlog.R
import top.fseasy.imlog.ui.components.overlaylayout.recordThumbnailBounds

object IMMediaDefaults {
  val MinWidth = 40.dp
  val MaxWidth = 220.dp
  val MinHeight = 40.dp
  val MaxHeight = 300.dp
  const val MIN_ASPECT_RATIO = 0.5f
  const val MAX_ASPECT_RATIO = 2.5f
}

fun calculateIMMediaSize(
    srcWidthPx: Int,
    srcHeightPx: Int,
    density: Density,
    minWidth: Dp = IMMediaDefaults.MinWidth,
    maxWidth: Dp = IMMediaDefaults.MaxWidth,
    minHeight: Dp = IMMediaDefaults.MinHeight,
    maxHeight: Dp = IMMediaDefaults.MaxHeight,
    minRatio: Float = IMMediaDefaults.MIN_ASPECT_RATIO,
    maxRatio: Float = IMMediaDefaults.MAX_ASPECT_RATIO,
): DpSize {
  if (srcWidthPx <= 0 || srcHeightPx <= 0) {
    return DpSize(minWidth, minHeight)
  }

  val originalWidthDp = with(density) { srcWidthPx.toDp() }
  val originalHeightDp = with(density) { srcHeightPx.toDp() }
  Timber.d("original dp = $originalWidthDp, $originalHeightDp")

  // 1. 先限制极端宽高比，得到受控的基准尺寸（可能比原图略大/略小）
  val rawRatio = srcWidthPx.toFloat() / srcHeightPx
  val (baseWidth, baseHeight) =
      when {
        rawRatio < minRatio -> originalHeightDp * minRatio to originalHeightDp // 太高 → 以高度为基准拉宽
        rawRatio > maxRatio -> originalWidthDp to originalWidthDp / maxRatio // 太宽 → 以宽度为基准拉高
        else -> originalWidthDp to originalHeightDp
      }

  // 2. 等比缩放：优先保持原尺寸，超出 max 再缩小，不足 min 再放大
  val scaleDown = minOf(maxWidth / baseWidth, maxHeight / baseHeight, 1f)
  val scaledW = baseWidth * scaleDown
  val scaledH = baseHeight * scaleDown

  val scaleUp =
      maxOf(
          if (scaledW < minWidth) minWidth / scaledW else 1f,
          if (scaledH < minHeight) minHeight / scaledH else 1f,
      )

  // 3. 最终尺寸（coerceIn 作为安全兜底，极端情况下可能轻微破坏比例）
  return DpSize(
      (scaledW * scaleUp).coerceIn(minWidth, maxWidth),
      (scaledH * scaleUp).coerceIn(minHeight, maxHeight),
  )
}

fun Modifier.imMediaConstraints(
    aspectRatio: Float,
    minWidth: Dp = IMMediaDefaults.MinWidth,
    maxWidth: Dp = IMMediaDefaults.MaxWidth,
    minHeight: Dp = IMMediaDefaults.MinHeight,
    maxHeight: Dp = IMMediaDefaults.MaxHeight,
    minRatio: Float = IMMediaDefaults.MIN_ASPECT_RATIO,
    maxRatio: Float = IMMediaDefaults.MAX_ASPECT_RATIO,
): Modifier =
    this.widthIn(min = minWidth, max = maxWidth)
        .heightIn(min = minHeight, max = maxHeight)
        .aspectRatio(aspectRatio.coerceIn(minRatio, maxRatio))

/** NOTE: there is no click function, please put click function to the upper level */
@Composable
fun MediaThumbnailBubble(
    url: Any?,
    widthPx: Int,
    heightPx: Int,
    imageMemoryCacheKey: String,
    sharedElementId: String,
    modifier: Modifier = Modifier,
    overlayContent: (@Composable BoxScope.() -> Unit)? = null,
) {
  val context = LocalContext.current
  val density = LocalDensity.current
  val mediaSize =
      remember(widthPx, heightPx, density) {
        calculateIMMediaSize(
            srcWidthPx = widthPx,
            srcHeightPx = heightPx,
            density = density,
        )
      }
  Timber.d("input size = $widthPx, $heightPx, result size = $mediaSize")
  val mediaRatio = widthPx / heightPx.toFloat().coerceAtLeast(1f)
  Box(
      modifier = modifier.size(mediaSize),
      contentAlignment = Alignment.Center,
  ) {
    // 1.unified thumbnail
    val imageRequest =
        remember(url, imageMemoryCacheKey) {
          ImageRequest.Builder(context).data(url).memoryCacheKey(imageMemoryCacheKey).build()
        }
    AsyncImage(
        model = imageRequest,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        filterQuality = FilterQuality.Medium,
        modifier =
            Modifier.fillMaxSize()
                .recordThumbnailBounds(sharedElementId, aspectRatio = mediaRatio), // fill box
        //        modifier = Modifier.matchParentSize(), // fill box
        fallback = painterResource(R.drawable.icon_broken_image),
        error = painterResource(R.drawable.icon_error),
        //        placeholder = painterResource(R.drawable.icon_donut_large),
    )

    // 2. overlay contents
    overlayContent?.invoke(this)
  }
}
