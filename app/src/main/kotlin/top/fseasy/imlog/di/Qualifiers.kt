package top.fseasy.imlog.di

import javax.inject.Qualifier


@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class ApplicationDefaultScope

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class ApplicationIoScope