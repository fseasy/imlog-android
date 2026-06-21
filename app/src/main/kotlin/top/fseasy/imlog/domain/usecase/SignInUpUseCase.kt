package top.fseasy.imlog.domain.usecase

import top.fseasy.imlog.domain.model.AvatarModel
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.model.UserPresetAvatar
import top.fseasy.imlog.domain.repository.AppStateRepository
import top.fseasy.imlog.domain.repository.ResourceProvider
import top.fseasy.imlog.domain.repository.StringArrayResId
import top.fseasy.imlog.domain.repository.UserRepository
import javax.inject.Inject

class SignInUpUseCase @Inject constructor(
    private val resourceProvider: ResourceProvider,
    private val userRepository: UserRepository,
    private val appStateRepository: AppStateRepository,
) {

    /**
     * @throws Exception
     * */
    suspend fun getLocalSignedInUsers() = userRepository.getLocalSignedInUsers()

    /**
     * @throws Exception
     * */
    suspend fun setCurrentUser(userId: UserId) = appStateRepository.setCurrentId(userId)

    /**
     * @throws Exception
     * */
    suspend fun createNewUserBySamplingProfile() {
        val username = sampleUsername()
        val avatar = samplePresetAvatar()
        userRepository.createAndSetCurrentUser(username = username, avatarModel = avatar)
    }

    private fun sampleUsername(): String {
        val adjectives = resourceProvider.getStringArray(StringArrayResId.RandomUsernameAdjectives)
        val nouns = resourceProvider.getStringArray(StringArrayResId.RandomUsernameNouns)
        val order = "%03d".format((0..999).random())
        return "${adjectives.random()}${nouns.random()}$order"
    }

    private fun samplePresetAvatar(): AvatarModel =
        AvatarModel.UserPreset(UserPresetAvatar.random())
}