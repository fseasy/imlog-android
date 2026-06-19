package top.fseasy.imlog.domain.usecase

import top.fseasy.imlog.domain.model.UserId
import javax.inject.Inject

/**
 * define path rules for the top-level storage buckets:
 * - shared storage: mainly for user message data (backup, sync)
 * - app-specific storage:
 *
 *    internal storage: mainly for db. We don't manage it here (hardcode it)
 *
 *    external storage - persistent
 *
 *    external storage - cache: like message media thumbnail
 *
 * basic rule:
 * - shared storage:
 *    root-uri: dirname contains app-name
 *    user storage root: $root/$user_id
 *
 * - external persistent+cache:
 *
 *    root-uri: platform dependent, don't care here.
 *
 *    user storage root: $root/$user_id/
 */
class StoragePathUseCase @Inject constructor() {
    /**
     * The root dir name for every user.
     */
    fun getUserRootDirName(userId: UserId): String = userId.value

}

