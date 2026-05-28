package top.fseasy.imtrace.app.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "imtrace_prefs")

data class AppPreferences(
    val currentUserId: String?,
    val themeMode: String,
    val lastRunVersionCode: Int
)

@Singleton
class AppPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val CURRENT_USER_ID = stringPreferencesKey("current_user_id")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LAST_RUN_VERSION_CODE = stringPreferencesKey("last_run_version_code")
    }

    val currentUserId: Flow<String?> = dataStore.data.map { prefs ->
        prefs[Keys.CURRENT_USER_ID]
    }

    val themeMode: Flow<String> = dataStore.data.map { prefs ->
        prefs[Keys.THEME_MODE] ?: "SYSTEM"
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
            prefs[Keys.LAST_RUN_VERSION_CODE] = versionCode.toString()
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }
}
