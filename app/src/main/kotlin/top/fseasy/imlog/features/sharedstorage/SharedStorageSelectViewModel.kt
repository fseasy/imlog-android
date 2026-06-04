package top.fseasy.imlog.features.sharedstorage

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.fseasy.imlog.data.datastore.AppPreferencesRepository
import javax.inject.Inject

class SharedStorageSelectViewModel @Inject constructor(
    private val repository: AppPreferencesRepository
) : ViewModel() {
    val storageUri = repository.sharedStorageRootUri
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun selectStorageUri(uri: Uri) {
        viewModelScope.launch {
            repository.setSharedStorageRootUri(uri)
        }
    }
}