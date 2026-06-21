package top.fseasy.imlog.ui.model

sealed interface TaskExecuteState {
    data object Idle : TaskExecuteState
    data object Executing : TaskExecuteState
    data class Failure(val reason: String) : TaskExecuteState
    data object Success : TaskExecuteState
}