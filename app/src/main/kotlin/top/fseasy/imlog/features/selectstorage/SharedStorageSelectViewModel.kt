package top.fseasy.imlog.features.selectstorage

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.fseasy.imlog.data.datastore.AppDataStore
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.UserRepository
import javax.inject.Inject

class SharedStorageSelectViewModel @Inject constructor(
    private val userRepository: UserRepository,
) : ViewModel() {

    fun selectStorageUri(userId: UserId, uri: Uri) {
        viewModelScope.launch {
            userRepository.setMediaStorageRootUriAndMark(userId, uri)
        }
    }
}