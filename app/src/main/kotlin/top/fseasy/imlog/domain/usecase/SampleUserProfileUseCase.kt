package top.fseasy.imlog.domain.usecase

import top.fseasy.imlog.domain.model.AvatarModel
import top.fseasy.imlog.domain.repository.ResourceProvider
import top.fseasy.imlog.domain.repository.StringArrayResId
import top.fseasy.imlog.domain.model.UserPresetAvatar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SampleUserProfileUseCase @Inject constructor(
    private val resourceProvider: ResourceProvider,
) {
    fun sampleUsername(): String {
        val adjectives =
            resourceProvider.getStringArray(StringArrayResId.RandomUsernameAdjectives)
        val nouns = resourceProvider.getStringArray(StringArrayResId.RandomUsernameNouns)
        val order = "%03d".format((0..999).random())
        return "${adjectives.random()}${nouns.random()}$order"
    }

    fun samplePresetAvatar(): AvatarModel = AvatarModel.UserPreset(UserPresetAvatar.random())
}

