package top.fseasy.imlog.domain.model

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface AuthState {
  data object Loading : AuthState

  data object Unauthenticated : AuthState

  data class Authenticated(val userId: UserId) : AuthState
}

/** Run instant if it's authenticated. */
inline fun CoroutineScope.launchWithAuth(
    authState: AuthState,
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
    crossinline block: suspend (userId: UserId) -> Unit,
): Job? {
  val userId = (authState as? AuthState.Authenticated)?.userId ?: return null
  return this.launch(coroutineContext) {
    block(userId)
  }
}

/** Waiting untile it's authenticated. */
inline fun CoroutineScope.launchUntilAuth(
    authStateFlow: StateFlow<AuthState>,
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
    crossinline block: suspend (userId: UserId) -> Unit,
): Job {
  return this.launch(coroutineContext) {
    val auth = authStateFlow.filterIsInstance<AuthState.Authenticated>().first()
    block(auth.userId)
  }
}
