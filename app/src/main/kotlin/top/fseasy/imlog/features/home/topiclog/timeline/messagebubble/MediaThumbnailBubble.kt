package top.fseasy.imlog.features.home.topiclog.timeline.messagebubble

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import top.fseasy.imlog.R
import top.fseasy.imlog.ui.components.sharedtransition.sharedThumbnail

object IMMediaDefaults {
  val MinWidth = 40.dp
  val MaxWidth = 220.dp
  val MinHeight = 40.dp
  val MaxHeight = 300.dp
  const val MIN_ASPECT_RATIO = 0.5f
  const val MAX_ASPECT_RATIO = 2.5f
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
    thumbnailUrl: Any?,
    aspectRatio: Float,
    imageMemoryCacheKey: String,
    sharedElementId: String,
    modifier: Modifier = Modifier,
    overlayContent: (@Composable BoxScope.() -> Unit)? = null,
) {
  val context = LocalContext.current

  Box(
      modifier = modifier.imMediaConstraints(aspectRatio),
      contentAlignment = Alignment.Center,
  ) {
    // 1.unified thumbnail
    val imageRequest =
        remember(thumbnailUrl, imageMemoryCacheKey) {
          ImageRequest.Builder(context)
              .data(thumbnailUrl)
              .memoryCacheKey(imageMemoryCacheKey)
              .build()
        }
    AsyncImage(
        model = imageRequest,
        contentDescription = null,
        contentScale = ContentScale.Inside,
        filterQuality = FilterQuality.Medium,
//        modifier = Modifier.matchParentSize().sharedThumbnail(sharedElementId), // fill box
        modifier = Modifier.matchParentSize(), // fill box
        fallback = painterResource(R.drawable.icon_broken_image),
        error = painterResource(R.drawable.icon_error),
        //        placeholder = painterResource(R.drawable.icon_donut_large),
    )

    // 2. overlay contents
    overlayContent?.invoke(this)
  }
}
