package top.fseasy.imlog.domain.recorder

sealed interface RecordingSessionState {
  data object Idle : RecordingSessionState

  data object Holding : RecordingSessionState

  data object Locked : RecordingSessionState
}
