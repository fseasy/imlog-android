package top.fseasy.imlog.features.home.topiclog.timeline.messagebubble

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import top.fseasy.imlog.R
import top.fseasy.imlog.features.home.topiclog.LocalTopicLogVisibilityScope
import top.fseasy.imlog.features.home.topiclog.LocalTopicLogSharedTransitionScope

object IMMediaDefaults {
  val MinWidth = 80.dp
  val MaxWidth = 220.dp
  val MinHeight = 80.dp
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

@Composable
fun MediaThumbnailBubble(
    id: String,
    thumbnailUrl: Any?,
    aspectRatio: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    overlayContent: (@Composable BoxScope.() -> Unit)? = null,
) {
  val sharedTransitionScope = LocalTopicLogSharedTransitionScope.current
  val visibilityScope = LocalTopicLogVisibilityScope.current

  val sharedTransitionModifier =
      if (sharedTransitionScope != null && visibilityScope != null) {
        with(sharedTransitionScope) {
          Modifier.sharedElement(
              rememberSharedContentState(key = "media_${id}"),
              animatedVisibilityScope = visibilityScope,
          )
        }
      } else {
        Modifier
      }

  Box(
      modifier =
          modifier
              .imMediaConstraints(aspectRatio)
              .clickable(onClick = onClick)
              .then(sharedTransitionModifier),
      contentAlignment = Alignment.Center,
  ) {
    // 1.unified thumbnail
    AsyncImage(
        model = thumbnailUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.matchParentSize(), // fill box
        fallback = painterResource(R.drawable.icon_broken_image),
        error = painterResource(R.drawable.icon_error),
        placeholder = painterResource(R.drawable.icon_donut_large),
    )

    // 2. overlay contents
    overlayContent?.invoke(this)
  }
}
