package top.fseasy.imlog.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.scopes.ActivityScoped
import top.fseasy.imlog.ui.commonmain.ExternalFileOpener
import top.fseasy.imlog.ui.util.AndroidExternalFileOpener

@Module
@InstallIn(ActivityComponent::class)
abstract class PlatformUiModule {

  @Binds
  @ActivityScoped
  abstract fun bindExternalFileOpener(
      impl: AndroidExternalFileOpener,
  ): ExternalFileOpener
}
