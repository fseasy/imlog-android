package top.fseasy.imlog.domain.usecase

import top.fseasy.imlog.R
import top.fseasy.imlog.domain.model.AvatarModel
import top.fseasy.imlog.domain.repository.ResourceProvider
import top.fseasy.imlog.domain.repository.StringArrayResId
import top.fseasy.imlog.ui.theme.PresetAvatar
import javax.inject.Inject

class SampleUserProfileUseCase @Inject constructor(
    private val resourceProvider: ResourceProvider,
) {
    fun sampleUsername(): String {
        val adjectives =
            resourceProvider.getStringArray(StringArrayResId(R.array.random_username_adjectives))
        val nouns = resourceProvider.getStringArray(StringArrayResId(R.array.random_username_nouns))
        val order = "%03d".format((0..999).random())
        return "${adjectives.random()}${nouns.random()}$order"
    }

    fun samplePresetAvatar(): AvatarModel = AvatarModel.Preset(PresetAvatar.random().dbName)
}

