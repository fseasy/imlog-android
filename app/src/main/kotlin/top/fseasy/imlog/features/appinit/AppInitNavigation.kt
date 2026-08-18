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
import top.fseasy.imlog.features.appinit.selectstorage.SharedStorageSelectScreen
import top.fseasy.imlog.features.auth.AuthGraph
import top.fseasy.imlog.features.auth.authGraph

/** Global entry in public */
@Serializable data object AppInitGraph

fun NavGraphBuilder.appInitGraph(
    navController: NavController,
    onInitSuccessNavigate: () -> Unit,
) {
  navigation<AppInitGraph>(
      startDestination = InitScreen.Dispatch,
  ) {
    composable<InitScreen.Dispatch>(
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
    ) {
      AppInitDispatch(
          onStepNavigate = { step ->
            when (step) {
              AppInitStep.Auth -> dispatchTo(navController, AuthGraph)
              AppInitStep.Finished -> onInitSuccessNavigate()
              is AppInitStep.SelectMediaStorageUri ->
                  dispatchTo(
                      navController,
                      InitScreen.SelectMediaStorageUri(step.userId.value),
                  )

              is AppInitStep.Welcome ->
                  dispatchTo(
                      navController,
                      InitScreen.Welcome(
                          userId = step.userId.value,
                          needCreateFirstTopic = step.needCreateFirstTopic,
                      ),
                  )
            }
          }
      )
    }

    authGraph(
        navController = navController,
        onAuthSuccessNavigate = {
          backToDispatch<AuthGraph>(navController)
        },
    )

    composable<InitScreen.SelectMediaStorageUri> { backStackEntry ->
      val route: InitScreen.SelectMediaStorageUri = backStackEntry.toRoute()
      SharedStorageSelectScreen(
          currentUserId = UserId(route.userId),
          onSuccessNavigate = {
            backToDispatch<InitScreen.SelectMediaStorageUri>(navController)
          },
      )
    }

    composable<InitScreen.Welcome> { backStackEntry ->
      val route: InitScreen.Welcome = backStackEntry.toRoute()
      WelcomeScreen(
          userId = UserId(route.userId),
          needCreateFirstTopic = route.needCreateFirstTopic,
          onSuccessNavigate = { backToDispatch<InitScreen.Welcome>(navController) },
      )
    }
  }
}

private sealed interface InitScreen {
  @Serializable data object Dispatch : InitScreen

  @Serializable data class SelectMediaStorageUri(val userId: String) : InitScreen

  @Serializable
  data class Welcome(val userId: String, val needCreateFirstTopic: Boolean) : InitScreen
}

private fun <T : Any> dispatchTo(navController: NavController, destination: T) {
  navController.navigate(destination) {
    popUpTo<InitScreen.Dispatch> {
      inclusive = true
    }
  }
}

private inline fun <reified CurrentRouteT : Any> backToDispatch(navController: NavController) {
  navController.navigate(InitScreen.Dispatch) {
    popUpTo<CurrentRouteT> {
      inclusive = true
    }
  }
}
