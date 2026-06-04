package top.fseasy.imlog.data.datastore

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val CURRENT_USER_ID = stringPreferencesKey("current_user_id")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LAST_RUN_VERSION_CODE = intPreferencesKey("last_run_version_code")
        val SHARED_STORAGE_ROOT_URI =
            stringPreferencesKey("shared_storage_root_uri")
    }

    val currentUserId: Flow<String?> = dataStore.data.map { prefs ->
        prefs[Keys.CURRENT_USER_ID]
    }

    val themeMode: Flow<String> = dataStore.data.map { prefs ->
        prefs[Keys.THEME_MODE] ?: "SYSTEM"
    }

    val lastRunVersionCode: Flow<Int> = dataStore.data.map { prefs ->
        prefs[Keys.LAST_RUN_VERSION_CODE] ?: 1
    }

    val sharedStorageRootUri: Flow<Uri?> = dataStore.data.map { prefs ->
        prefs[Keys.SHARED_STORAGE_ROOT_URI]?.toUri()
    }

    suspend fun setCurrentUserId(userId: String?) {
        dataStore.edit { prefs ->
            if (userId != null) {
                prefs[Keys.CURRENT_USER_ID] = userId
            } else {
                prefs.remove(Keys.CURRENT_USER_ID)
            }
        }
    }

    suspend fun setThemeMode(mode: String) {
        dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode
        }
    }

    suspend fun setLastRunVersionCode(versionCode: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.LAST_RUN_VERSION_CODE] = versionCode
        }
    }

    suspend fun setSharedStorageRootUri(uri: Uri?) {
        dataStore.edit { prefs ->
            if (uri != null) {
                prefs[Keys.SHARED_STORAGE_ROOT_URI] = uri.toString()
            } else {
                prefs.remove(Keys.SHARED_STORAGE_ROOT_URI)
            }
        }
    }
}

// preferencesDataStore 必须依赖 Context （依赖 context.filesDir）.
// 所以将其注入到 Context 内部，才能这样使用
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "imlog_prefs")

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }
}
