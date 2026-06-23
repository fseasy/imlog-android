package top.fseasy.imlog.features.auth

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import kotlinx.serialization.Serializable
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.features.auth.ui.SignInUpCreateLocalUserScreen
import top.fseasy.imlog.features.auth.ui.AuthSelectLocalUserScreen


@Serializable
data object AuthGraph

private sealed interface AuthScreen {
    @Serializable
    data object CreateUser : AuthScreen

    @Serializable
    data object SelectUser : AuthScreen
}

fun NavGraphBuilder.authGraph(
    navController: NavController,
    onAuthSuccessNavigate: () -> Unit,
) {
    navigation<AuthGraph>(
        startDestination = AuthScreen.SelectUser,
    ) {
        composable<AuthScreen.CreateUser> {
            SignInUpCreateLocalUserScreen(onAuthSuccessNavigate = onAuthSuccessNavigate)
        }
        composable<AuthScreen.SelectUser> {
            AuthSelectLocalUserScreen(
                onNavigateToCreateUser = {
                    navController.navigate(AuthScreen.CreateUser) {
                        popUpTo(AuthScreen.SelectUser) { inclusive = true }
                    }
                }, onAuthSuccessNavigate = onAuthSuccessNavigate
            )
        }
    }
}