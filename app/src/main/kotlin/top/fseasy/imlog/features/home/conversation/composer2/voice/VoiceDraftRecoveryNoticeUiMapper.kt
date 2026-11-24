package top.fseasy.imlog.features.home.conversation.composer2.voice

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import top.fseasy.imlog.R
import top.fseasy.imlog.domain.usecase.composer.DraftRecoveryNotice

// ==========================================
// 1. 给 ObserveAsEvents / Snackbar 使用（配合 LocalResources.current）
// ==========================================

fun DraftRecoveryNotice.toMessage(resources: Resources): String =
    resources.getString(toMessageRes())

fun DraftRecoveryNotice.toActionLabel(resources: Resources): String? = quarantinedFilePath?.let {
  resources.getString(R.string.term_view_file)
}

// ==========================================
// 2. 给 @Composable 界面直接渲染使用（如 Banner）
// ==========================================

@Composable fun DraftRecoveryNotice.asString(): String = stringResource(toMessageRes())

// ==========================================
// 3. 纯资源 ID 映射
// ==========================================

@StringRes
private fun DraftRecoveryNotice.toMessageRes(): Int {
  val hasBackup = quarantinedFilePath != null
  return when (this) {
    is DraftRecoveryNotice.AppendingSegmentDiscarded ->
        if (hasBackup) R.string.composer_voice_draft_recovery_appending_discarded_with_backup
        else R.string.composer_voice_draft_recovery_appending_discarded

    is DraftRecoveryNotice.BaseSegmentLostKeptPending ->
        if (hasBackup) R.string.composer_voice_draft_recovery_base_lost_kept_pending_with_backup
        else R.string.composer_voice_draft_recovery_base_lost_kept_pending

    is DraftRecoveryNotice.VoiceDraftCorrupted ->
        if (hasBackup) R.string.composer_voice_draft_recovery_corrupted_with_backup
        else R.string.composer_voice_draft_recovery_corrupted
  }
}
