package top.fseasy.imlog.features.appinit

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.features.appinit.ui.AppInitDispatch
import top.fseasy.imlog.features.appinit.ui.WelcomeScreen
import top.fseasy.imlog.features.auth.AuthGraph
import top.fseasy.imlog.features.auth.authGraph
import top.fseasy.imlog.features.selectstorage.SharedStorageSelectScreen

/**
 * Global entry in public
 */
@Serializable
data object AppInitGraph

fun NavGraphBuilder.appInitGraph(
    navController: NavController,
    onInitSuccessNavigate: () -> Unit,
) {
    navigation<AppInitGraph>(startDestination = InitScreen.Dispatch) {
        composable<InitScreen.Dispatch>(
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
        ) {
            AppInitDispatch(onStepNavigate = { step ->
                when (step) {
                    AppInitStep.Auth -> dispatchTo(navController, AuthGraph)
                    AppInitStep.Finished -> onInitSuccessNavigate()
                    is AppInitStep.SelectMediaStorageUri -> dispatchTo(
                        navController, InitScreen.SelectMediaStorageUri(step.userId)
                    )

                    is AppInitStep.Welcome -> dispatchTo(
                        navController, InitScreen.Welcome(
                            userId = step.userId, needCreateFirstTopic = step.needCreateFirstTopic
                        )
                    )
                }
            })
        }

        authGraph(navController = navController, onAuthSuccessNavigate = {
            backToDispatch(navController, AuthGraph)
        })

        composable<InitScreen.SelectMediaStorageUri> { backStackEntry ->
            val route: InitScreen.SelectMediaStorageUri = backStackEntry.toRoute()
            SharedStorageSelectScreen(currentUserId = route.userId, onSuccessNavigate = {
                backToDispatch(navController, InitScreen.SelectMediaStorageUri)
            })
        }

        composable<InitScreen.Welcome> { backStackEntry ->
            val route: InitScreen.Welcome = backStackEntry.toRoute()
            WelcomeScreen(
                userId = route.userId,
                needCreateFirstTopic = route.needCreateFirstTopic,
                onSuccessNavigate = { backToDispatch(navController, InitScreen.Welcome) })
        }
    }
}

private sealed interface InitScreen {
    @Serializable
    data object Dispatch : InitScreen

    @Serializable
    data class SelectMediaStorageUri(val userId: UserId) : InitScreen

    @Serializable
    data class Welcome(val userId: UserId, val needCreateFirstTopic: Boolean) : InitScreen
}


private fun <T : Any> dispatchTo(navController: NavController, destination: T) {
    navController.navigate(destination) {
        popUpTo(InitScreen.Dispatch) {
            inclusive = true
        }
    }
}

private fun <T : Any> backToDispatch(navController: NavController, source: T) {
    navController.navigate(InitScreen.Dispatch) {
        popUpTo(source) {
            inclusive = true
        }
    }
}