package top.fseasy.imlog.domain.model

sealed interface AuthState {
  data object Loading : AuthState

  data object Unauthenticated : AuthState

  data class Authenticated(val userId: UserId) : AuthState
}
