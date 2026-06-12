package top.fseasy.imlog.features.appinit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.fseasy.imlog.domain.repository.UserRepository
import javax.inject.Inject



@HiltViewModel
class AppInitViewModel @Inject constructor(
    private val userRepository: UserRepository
): ViewModel() {
    private val _initState = MutableStateFlow<AppInitState>(AppInitState.Loading)
    val initState = _initState.asStateFlow()

    fun startInit() {
        viewModelScope.launch {

        }
    }
}