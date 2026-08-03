package top.fseasy.imlog.ui.model

import androidx.compose.runtime.Immutable

/**
 * A common ui state for task execution
 *
 * @see top.fseasy.imlog.ui.components.TaskStateLoadingWrapper to know the loading component helper
 */
sealed interface TaskExecuteState<out T> {
  data object Idle : TaskExecuteState<Nothing>

  data object Executing : TaskExecuteState<Nothing>

  data class Failure(val reason: String) : TaskExecuteState<Nothing>

  data class Success<T>(val data: T) : TaskExecuteState<T>
}

@Immutable
sealed interface TaskExecuteWithDefaultState<out T> {
  val data: T

  @Immutable data class Idle<T>(override val data: T) : TaskExecuteWithDefaultState<T>

  @Immutable data class Executing<T>(override val data: T) : TaskExecuteWithDefaultState<T>

  @Immutable
  data class Failure<T>(override val data: T, val reason: String) : TaskExecuteWithDefaultState<T>

  @Immutable data class Success<T>(override val data: T) : TaskExecuteWithDefaultState<T>
}
