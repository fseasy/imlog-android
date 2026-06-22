package top.fseasy.imlog.ui.model

/**
 * A common ui state for task execution
 * @see top.fseasy.imlog.ui.components.TaskStateLoadingWrapper to know the loading component helper
 */
sealed interface TaskExecuteState<out T> {
    data object Idle : TaskExecuteState<Nothing>
    data object Executing : TaskExecuteState<Nothing>
    data class Failure(val reason: String) : TaskExecuteState<Nothing>
    data class Success<T>(val data: T) : TaskExecuteState<T>
}