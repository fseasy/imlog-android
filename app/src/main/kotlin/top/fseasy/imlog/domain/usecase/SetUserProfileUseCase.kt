package top.fseasy.imlog.domain.usecase

import top.fseasy.imlog.domain.repository.UserRepository
import javax.inject.Inject

class SetUserProfileUseCase @Inject constructor(
    private val userRepository: UserRepository,
) {

}