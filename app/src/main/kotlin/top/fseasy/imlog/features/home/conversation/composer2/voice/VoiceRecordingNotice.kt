package top.fseasy.imlog.features.home.conversation.composer2.voice

import top.fseasy.imlog.domain.audio.AudioRecordError
import top.fseasy.imlog.domain.audio.MergeFailureReason
import top.fseasy.imlog.domain.audio.RecordingOsEvent

sealed interface VoiceRecordingNotice {
  data class StopSuccessOnOsEvent(val osEvent: RecordingOsEvent) : VoiceRecordingNotice

  data class StopFailure(
      val recordError: AudioRecordError?,
      val mergeFailureReason: MergeFailureReason?,
      val osEvent: RecordingOsEvent?,
      val couldRollback: Boolean,
  ) : VoiceRecordingNotice

  data class StartFailure(
      val recordError: AudioRecordError?,
      val isAppending: Boolean,
  ) : VoiceRecordingNotice
}
