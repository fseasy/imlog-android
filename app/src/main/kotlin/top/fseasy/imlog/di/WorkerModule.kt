package top.fseasy.imlog.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import top.fseasy.imlog.domain.repository.WorkerRunner
import top.fseasy.imlog.worker.WorkerRunnerImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkerModule {
    @Binds
    abstract fun bindWorkerRunner(impl: WorkerRunnerImpl): WorkerRunner
}