package top.fseasy.imlog.features.home.conversation.composer2.voice

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import top.fseasy.imlog.R
import top.fseasy.imlog.domain.audio.AudioRecordError
import top.fseasy.imlog.domain.audio.RecordingOsEvent

// ==========================================
// 1. 给 ObserveAsEvents / Snackbar 使用（配合 LocalResources.current）
// ==========================================

fun VoiceRecordingNotice.toMessage(resources: Resources): String {
  val result = resources.getString(toResultRes())
  return resources.getString(toReasonRes(), result)
}

// ==========================================
// 2. 给 @Composable 界面直接渲染使用（如 Banner）
// ==========================================

@Composable
fun VoiceRecordingNotice.asString(): String =
    stringResource(toReasonRes(), stringResource(toResultRes()))

// ==========================================
// 3. 纯资源 ID 映射
// ==========================================

@StringRes
private fun VoiceRecordingNotice.toResultRes(): Int =
    when (this) {
      is VoiceRecordingNotice.StopSuccessOnOsEvent ->
          R.string.composer_recording_success_result_saved

      is VoiceRecordingNotice.StopFailure ->
          if (couldRollback) R.string.composer_recording_fail_result_rollback
          else R.string.composer_recording_fail_result_cannot_save

      is VoiceRecordingNotice.StartFailure ->
          if (isAppending) R.string.composer_recording_fail_result_cannot_append
          else R.string.composer_recording_fail_result_cannot_start
    }

@StringRes
private fun VoiceRecordingNotice.toReasonRes(): Int =
    when (this) {
      is VoiceRecordingNotice.StopSuccessOnOsEvent ->
          resolveReasonRes(recordError = null, osEvent = osEvent)

      is VoiceRecordingNotice.StopFailure ->
          resolveReasonRes(recordError = recordError, osEvent = osEvent)

      is VoiceRecordingNotice.StartFailure ->
          resolveReasonRes(recordError = recordError, osEvent = null)
    }

@StringRes
private fun resolveReasonRes(
    recordError: AudioRecordError?,
    osEvent: RecordingOsEvent?,
): Int =
    when (osEvent) {
      RecordingOsEvent.FocusLost -> R.string.composer_recording_fail_reason_focus_lost
      RecordingOsEvent.OsNativeError -> {
        // OsNativeError 精度不足时可能是 StorageFull，保留原有的双保险判断
        if (recordError is AudioRecordError.StorageFull) {
          R.string.composer_recording_fail_reason_storage_full
        } else {
          R.string.composer_recording_fail_reason_os_native_error
        }
      }
      RecordingOsEvent.StorageFull -> R.string.composer_recording_fail_reason_storage_full
      null ->
          when (recordError) {
            AudioRecordError.FileIo -> R.string.composer_recording_fail_reason_file_io
            AudioRecordError.MicrophoneBusy ->
                R.string.composer_recording_fail_reason_microphone_busy
            AudioRecordError.PermissionDenied ->
                R.string.composer_recording_fail_reason_permission_denied
            AudioRecordError.StorageFull -> R.string.composer_recording_fail_reason_storage_full
            AudioRecordError.TooShort -> R.string.composer_recording_fail_reason_too_short
            AudioRecordError.RecorderInternal,
            null -> R.string.composer_recording_fail_reason_internal_error
          }
    }
