package top.fseasy.imlog.features.home.topiclog.timeline.messagebubble

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.features.home.topiclog.LocalTopicLogSharedTransitionScope
import top.fseasy.imlog.features.home.topiclog.LocalTopicLogVisibilityScope
import top.fseasy.imlog.features.home.topiclog.toSharedTransitionElementId

@Composable
fun TextMessageBubble(
    messageId: MessageId,
    text: String,
    color: Color = LocalContentColor.current,
) {

  val sharedTransitionScope = LocalTopicLogSharedTransitionScope.current
  val animatedVisibilityScope = LocalTopicLogVisibilityScope.current

  val sharedModifier =
      if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
          Modifier.sharedElement(
              rememberSharedContentState(key = toSharedTransitionElementId(messageId)),
              animatedVisibilityScope = animatedVisibilityScope,
          )
        }
      } else {
        Modifier
      }

  Text(
      text = text,
      modifier = Modifier.then(sharedModifier).padding(12.dp),
      style = TextMessageTypograph,
      color = color,
  )
}
