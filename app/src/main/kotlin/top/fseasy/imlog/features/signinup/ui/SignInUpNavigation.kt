package top.fseasy.imlog.features.signinup.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import top.fseasy.imlog.features.signinup.SignInUpRoute
import top.fseasy.imlog.features.signinup.SignInUpViewModel

@Composable
fun SignInUpNavigation(
    navController: NavHostController = rememberNavController(),
    viewModel: SignInUpViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    NavHost(
        navController = navController, startDestination = SignInUpRoute.Loading
    ) {
        composable<SignInUpRoute.Loading> {
            SignInUpLoadingScreen(
                uiState, onNavigate = { destination ->
                    navController.navigate(destination) {
                        // destroy Loading
                        popUpTo(SignInUpRoute.Loading) { inclusive = true }
                    }
                })
        }
        composable<SignInUpRoute.SelectUser> {
            SignInUpSelectUserScreen(
                uiState.users,
                onSelectUserClick = { u -> viewModel.selectUser(u) },
                onNavigateToCreateUser = {
                    navController.navigate(SignInUpRoute.CreateUser) {
                        popUpTo(SignInUpRoute.SelectUser) { inclusive = true }
                    }
                })
        }
        composable<SignInUpRoute.CreateUser> {
            SignInUpCreateUserScreen(
                uiState.createUserState,
                onCreateUser = { viewModel.createUser() },
            )
        }
    }
}