package top.fseasy.imlog.features.signinup

import kotlinx.serialization.Serializable


sealed interface SignInUpRoute {
    @Serializable
    data object Loading : SignInUpRoute

    @Serializable
    data object CreateUser : SignInUpRoute
    @Serializable
    data object SelectUser : SignInUpRoute
}