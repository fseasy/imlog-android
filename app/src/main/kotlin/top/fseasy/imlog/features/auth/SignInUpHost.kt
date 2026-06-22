package top.fseasy.imlog.features.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import top.fseasy.imlog.features.auth.ui.SignInUpCreateUserScreen
import top.fseasy.imlog.features.auth.ui.SignInUpLoadingScreen
import top.fseasy.imlog.features.auth.ui.AuthSelectLocalUserScreen

@Composable
fun SignInUpHost(
    navController: NavHostController = rememberNavController(),
    viewModel: SignInUpSharedViewModel = hiltViewModel(),
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
            AuthSelectLocalUserScreen(
                uiState.users,
                onSelectUserClick = { u -> viewModel.selectUser(u) },
                onNavigateToCreateUser = {
                    navController.navigate(SignInUpRoute.CreateUser) {
                        popUpTo(SignInUpRoute.SelectUser) { inclusive = true }
                    }
                })
        }
        composable<SignInUpRoute.CreateUser> {
            SignInUpCreateUserScreen()
        }
    }
}