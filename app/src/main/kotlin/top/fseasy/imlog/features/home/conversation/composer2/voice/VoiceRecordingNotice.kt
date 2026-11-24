package top.fseasy.imlog.features.home.conversation.composer2.voice

import android.content.Context
import top.fseasy.imlog.R
import top.fseasy.imlog.domain.audio.AudioRecordError
import top.fseasy.imlog.domain.audio.RecordingOsEvent
import top.fseasy.imlog.domain.usecase.composer.DraftRecoveryNotice
import top.fseasy.imlog.features.home.conversation.SnackbarAction

internal fun generateTipsForDraftRecovery(
    context: Context,
    notice: DraftRecoveryNotice,
): Pair<String, SnackbarAction?> {
  val hasBackup = notice.quarantinedFilePath != null

  val messageRes =
      when (notice) {
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

  val message = context.getString(messageRes)
  val action = notice.quarantinedFilePath?.let(SnackbarAction::ViewQuarantinedAudio)
  return message to action
}

sealed interface VoiceRecordingNotice {
  data class StopSuccessOnOsEvent(val osEvent: RecordingOsEvent) : VoiceRecordingNotice

  data class StopFailure(
      val recordError: AudioRecordError?,
      val osEvent: RecordingOsEvent?,
      val couldRollback: Boolean,
  ) : VoiceRecordingNotice

  data class StartFailure(
      val recordError: AudioRecordError?,
      val isAppending: Boolean,
  ) : VoiceRecordingNotice
}
