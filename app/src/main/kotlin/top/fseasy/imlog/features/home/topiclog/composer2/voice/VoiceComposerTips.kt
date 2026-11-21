package top.fseasy.imlog.features.home.topiclog.composer2.voice

import android.content.Context
import top.fseasy.imlog.R
import top.fseasy.imlog.domain.audio.AudioRecordError
import top.fseasy.imlog.domain.audio.RecordingOsEvent

internal fun generateTipsForOsEventCausedStopSuccess(
    context: Context,
    osEventTrigger: RecordingOsEventSession?,
): String? {
  val reasonResId =
      when (osEventTrigger?.event) {
        RecordingOsEvent.FocusLost -> R.string.composer_recording_fail_reason_focus_lost
        RecordingOsEvent.OsNativeError -> R.string.composer_recording_fail_reason_os_native_error
        RecordingOsEvent.StorageFull -> R.string.composer_recording_fail_reason_storage_full
        null -> null
      } ?: return null
  val result = context.getString(R.string.composer_recording_success_result_saved)
  return context.getString(reasonResId, result)
}

internal fun generateTipsForStopFailure(
    context: Context,
    recordError: AudioRecordError?,
    osEventTrigger: RecordingOsEventSession?,
    couldRollback: Boolean,
): String {
  val resultResId =
      if (couldRollback) R.string.composer_recording_fail_result_rollback
      else R.string.composer_recording_fail_result_cannot_save
  val result = context.getString(resultResId)
  val message =
      joinFailureReasonWithResult(
          context = context,
          recordError = recordError,
          osEventTrigger = osEventTrigger,
          result = result,
      )
  return message
}

internal fun generateTipsForStartFailure(
    context: Context,
    recordError: AudioRecordError?,
    isAppending: Boolean,
): String {
  val resultResId =
      if (isAppending) R.string.composer_recording_fail_result_cannot_append
      else R.string.composer_recording_fail_result_cannot_start
  val result = context.getString(resultResId)
  val message =
      joinFailureReasonWithResult(
          context = context,
          recordError = recordError,
          osEventTrigger = null,
          result = result,
      )
  return message
}

/** FailureTips = $reason. $result */
private fun joinFailureReasonWithResult(
    context: Context,
    recordError: AudioRecordError?,
    osEventTrigger: RecordingOsEventSession?,
    result: String,
): String {
  // we should first show osEventTrigger reason as it's from non-user intention.

  val storageFullResId = R.string.composer_recording_fail_reason_storage_full
  val reasonResId =
      when (osEventTrigger?.event) {
        RecordingOsEvent.FocusLost -> R.string.composer_recording_fail_reason_focus_lost
        RecordingOsEvent.OsNativeError -> {
          // But as we know, `RecordingOsEvent.OsNativeError` maybe not accurate enough
          // -- it can be StorageFull, so in this condition, we make an extra double-check
          if (recordError is AudioRecordError.StorageFull) storageFullResId
          else R.string.composer_recording_fail_reason_os_native_error
        }
        RecordingOsEvent.StorageFull -> storageFullResId
        null ->
            when (recordError) {
              AudioRecordError.FileIo -> R.string.composer_recording_fail_reason_file_io
              AudioRecordError.MicrophoneBusy ->
                  R.string.composer_recording_fail_reason_microphone_busy
              AudioRecordError.PermissionDenied ->
                  R.string.composer_recording_fail_reason_permission_denied
              AudioRecordError.StorageFull -> storageFullResId
              AudioRecordError.TooShort -> R.string.composer_recording_fail_reason_too_short
              AudioRecordError.RecorderInternal,
              null -> R.string.composer_recording_fail_reason_internal_error
            }
      }
  return context.getString(reasonResId, result)
}
