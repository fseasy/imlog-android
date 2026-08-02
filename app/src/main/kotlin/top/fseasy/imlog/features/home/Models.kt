package top.fseasy.imlog.features.home

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import top.fseasy.imlog.R

data class ListItemUiResource(
    @StringRes val supportingStringRes: Int,
    @StringRes val buttonStringRes: Int,
    @DrawableRes val iconRes: Int,
)

fun getPinListItemResource(isPinned: Boolean): ListItemUiResource {
  return if (isPinned) {
    ListItemUiResource(
        supportingStringRes = R.string.topic_settings_pinned_status,
        buttonStringRes = R.string.topic_settings_btn_unpin,
        iconRes = R.drawable.icon_keep_off,
    )
  } else {
    ListItemUiResource(
        supportingStringRes = R.string.topic_settings_unpinned_status,
        buttonStringRes = R.string.topic_settings_btn_pin,
        iconRes = R.drawable.icon_keep,
    )
  }
}

fun getArchiveListItemResource(isArchived: Boolean): ListItemUiResource {
  return if (isArchived) {
    ListItemUiResource(
        supportingStringRes = R.string.topic_settings_archived_status,
        buttonStringRes = R.string.topic_settings_btn_unarchive,
        iconRes = R.drawable.icon_archive,
    )
  } else {
    ListItemUiResource(
        supportingStringRes = R.string.topic_settings_unarchived_status,
        buttonStringRes = R.string.topic_settings_btn_archive,
        iconRes = R.drawable.icon_archive,
    )
  }
}
