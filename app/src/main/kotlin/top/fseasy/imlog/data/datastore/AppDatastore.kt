package top.fseasy.imlog.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
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
class AppDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val LAST_RUN_VERSION_CODE = intPreferencesKey("last_run_version_code")
    }


    val lastRunVersionCode: Flow<Int> = dataStore.data.map { prefs ->
        prefs[Keys.LAST_RUN_VERSION_CODE] ?: 1
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
