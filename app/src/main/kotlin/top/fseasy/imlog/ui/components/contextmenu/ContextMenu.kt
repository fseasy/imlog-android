package top.fseasy.imlog.ui.components.contextmenu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

data class ContextMenuItem(
    val title: String,
    val iconVector: ImageVector? = null,
    val isDestructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Vertical Context Menu.
 *
 * Usage:
 *
 * 1. in the top/parent component, define a [ContextMenuState]
 *
 *    ```
 *    val contextMenuState = remeberContextMenuState<UiModel>()
 *    ```
 * 2. In the top/parent component, define this menu:
 *    ```
 *    VerticalContextMenu(
 *      state = contextMenuState,
 *      items = { currentModel ->
 *        listOf(
 *          ContextMenuItem(....)
 *        )
 *      }
 *    )
 *    ```
 *
 * @see top.fseasy.imlog.features.home.main.HomeTopicList TopicItemListContent
 */
@Composable
fun <T> VerticalContextMenu(
    state: ContextMenuState<T>,
    items: @Composable (target: T) -> List<ContextMenuItem>,
    modifier: Modifier = Modifier,
) {
  val currentTarget = state.target ?: return

  Popup(
      popupPositionProvider = VerticalListMenuPositionProvider(state.position),
      onDismissRequest = state::dismiss,
      properties =
          PopupProperties(
              focusable = true,
              dismissOnBackPress = true,
              dismissOnClickOutside = true,
          ),
  ) {
    // 纵向卡片遵循 M3 标准弹层配色（高对比度 elevated container）
    Surface(
        modifier =
            modifier
                // 宽度按内容最大项自适应，并设定最小宽度（符合 M3 菜单规范）
                .width(IntrinsicSize.Max)
                .widthIn(min = 160.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 4.dp,
        tonalElevation = 0.dp,
    ) {
      Column(modifier = Modifier.padding(vertical = 6.dp)) {
        items(currentTarget).forEach { action ->
          // 如果是危险操作，颜色切换为系统标准的 error 红色
          val itemColor =
              if (action.isDestructive) {
                MaterialTheme.colorScheme.error
              } else {
                LocalContentColor.current
              }

          Row(
              modifier =
                  Modifier.fillMaxWidth()
                      .clickable {
                        state.dismiss()
                        action.onClick()
                      }
                      .padding(horizontal = 16.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
          ) {
            // 如果有图标，在左侧显示
            if (action.iconVector != null) {
              Icon(
                  action.iconVector,
                  contentDescription = null,
                  tint = itemColor,
                  modifier = Modifier.size(20.dp),
              )
              Spacer(modifier = Modifier.width(12.dp))
            }

            Text(
                text = action.title,
                style = MaterialTheme.typography.bodyMedium,
                color = itemColor,
            )
          }
        }
      }
    }
  }
}

/**
 * Usage:
 *
 * @see VerticalContextMenu
 */
@Composable
fun <T> HorizontalContextMenu(
    state: ContextMenuState<T>,
    items: @Composable (T) -> List<ContextMenuItem>,
    modifier: Modifier = Modifier,
) {
  val currentTarget = state.target ?: return
  Popup(
      popupPositionProvider = HorizontalBubbleMenuPositionProvider(state.position),
      onDismissRequest = state::dismiss,
      properties =
          PopupProperties(
              focusable = true,
              dismissOnBackPress = true,
              dismissOnClickOutside = true,
          ),
  ) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 4.dp,
        tonalElevation = 6.dp,
    ) {
      Row(
          modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp).height(IntrinsicSize.Min),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        val renderItems = items(currentTarget)
        renderItems.forEachIndexed { index, action ->
          Column(
              modifier =
                  Modifier.clickable {
                        state.dismiss()
                        action.onClick()
                      }
                      .padding(horizontal = 12.dp, vertical = 6.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
          ) {
            val itemColor =
                if (action.isDestructive) {
                  MaterialTheme.colorScheme.error
                } else {
                  LocalContentColor.current
                }

            if (action.iconVector != null) {
              Icon(
                  action.iconVector,
                  contentDescription = action.title,
                  modifier = Modifier.size(20.dp),
                  tint = itemColor,
              )

              Spacer(modifier = Modifier.height(4.dp))
            }

            Text(
                text = action.title,
                style = MaterialTheme.typography.labelSmall,
                color = itemColor,
            )
          }

          if (index < renderItems.size - 1) {
            VerticalDivider(
                modifier = Modifier.fillMaxHeight().padding(vertical = 8.dp),
                thickness = 0.5.dp,
                color = LocalContentColor.current.copy(alpha = 0.2f),
            )
          }
        }
      }
    }
  }
}
