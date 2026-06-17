package top.fseasy.imlog.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import top.fseasy.imlog.data.repository.AppStateRepositoryImpl
import top.fseasy.imlog.data.repository.MessageRepositoryImpl
import top.fseasy.imlog.data.repository.ResourceProviderImpl
import top.fseasy.imlog.data.repository.TopicRepositoryImpl
import top.fseasy.imlog.data.repository.UserRepositoryImpl
import top.fseasy.imlog.domain.repository.AppStateRepository
import top.fseasy.imlog.domain.repository.MessageRepository
import top.fseasy.imlog.domain.repository.ResourceProvider
import top.fseasy.imlog.domain.repository.TopicRepository
import top.fseasy.imlog.domain.repository.UserRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindTopicRepository(
        topicRepositoryImpl: TopicRepositoryImpl,
    ): TopicRepository

    @Binds
    abstract fun bindUserRepository(
        userRepositoryImpl: UserRepositoryImpl,
    ): UserRepository

    @Binds
    abstract fun bindMessageRepository(
        messageRepositoryImpl: MessageRepositoryImpl,
    ): MessageRepository

    @Binds
    abstract fun bindAppStateRepository(
        appStateRepositoryImpl: AppStateRepositoryImpl,
    ): AppStateRepository

    @Binds
    abstract fun bindResourceProvider(
        resourceProviderImpl: ResourceProviderImpl,
    ): ResourceProvider
}