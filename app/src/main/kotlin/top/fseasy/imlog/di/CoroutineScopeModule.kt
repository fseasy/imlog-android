package top.fseasy.imlog.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object CoroutineScopeModule {
  @Provides
  @Singleton
  @ApplicationDefaultScope
  fun provideApplicationDefaultScope(): CoroutineScope {
    return CoroutineScope(SupervisorJob() + Dispatchers.Default)
  }

  @Provides
  @Singleton
  @ApplicationIoScope
  fun provideApplicationIoScope(): CoroutineScope {
    return CoroutineScope(SupervisorJob() + Dispatchers.IO)
  }
}
