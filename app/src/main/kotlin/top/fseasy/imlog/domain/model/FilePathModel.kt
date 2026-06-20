package top.fseasy.imlog.domain.model

/**
 * To flag it is in internal-persistent or internal-cache location
 */
enum class InternalLocation {
    Persistent, Cache
}

sealed interface SharedStorageRootSource {
    data class Direct(val uriStr: UriStr) : SharedStorageRootSource
    data class LookupByUser(val userId: UserId) : SharedStorageRootSource
}

/**
 * Used to fit the clean architecture requirements
 * @see top.fseasy.imlog.domain.usecase.StoragePathUseCase to know the storage choose reason
 */
sealed interface FilePathModel {
    val fullRelativePath: List<String>

    /**
     * @param userId needs this to
     */
    data class SharedStorageOnly(
        override val fullRelativePath: List<String>,
        val root: SharedStorageRootSource,
    ) : FilePathModel

    data class InternalOnly(
        override val fullRelativePath: List<String>,
        val internalLocation: InternalLocation,
    ) : FilePathModel

    data class DualWrite(
        override val fullRelativePath: List<String>,
        val root: SharedStorageRootSource,
        val internalLocation: InternalLocation,
    ) : FilePathModel {
        fun toSharedStorageOnly() = SharedStorageOnly(fullRelativePath, root)
        fun toInternalOnly() = InternalOnly(fullRelativePath, internalLocation)
    }
}

