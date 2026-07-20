package top.fseasy.imlog.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import top.fseasy.imlog.data.service.ThumbnailServiceImpl
import top.fseasy.imlog.domain.service.ThumbnailService

@Module
@InstallIn(SingletonComponent::class)
abstract class ServiceModule {
    @Binds
    abstract fun bindThumbnailService(impl: ThumbnailServiceImpl): ThumbnailService
}