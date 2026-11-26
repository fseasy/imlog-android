package top.fseasy.imlog.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import top.fseasy.imlog.data.audio.AdtsVoiceDraftAudioProcessor
import top.fseasy.imlog.data.audio.AndroidAudioWaveformExtractor
import top.fseasy.imlog.domain.audio.AudioWaveformExtractor
import top.fseasy.imlog.domain.audio.VoiceDraftAudioProcessor

@Module
@InstallIn(ViewModelComponent::class)
abstract class DataAudioModule {
  @Binds
  abstract fun bindVoiceDraftAudioProcessor(
      impl: AdtsVoiceDraftAudioProcessor,
  ): VoiceDraftAudioProcessor

  @Binds
  abstract fun bindAudioWaveformExtractor(
      impl: AndroidAudioWaveformExtractor,
  ): AudioWaveformExtractor
}
