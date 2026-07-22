package top.fseasy.imlog.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import top.fseasy.imlog.domain.repository.BackgroundTaskRunner
import top.fseasy.imlog.worker.BackgroundTaskRunnerImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class BackgroundTaskRunnerModule {
    @Binds
    abstract fun bindBackgroundTaskRunner(impl: BackgroundTaskRunnerImpl): BackgroundTaskRunner
}