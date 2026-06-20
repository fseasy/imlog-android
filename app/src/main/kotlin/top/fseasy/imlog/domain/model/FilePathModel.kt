package top.fseasy.imlog.domain.model

/**
 * To flag it is in external-persistent or external-cache location
 */
enum class ExternalLocation {
    Persistent, Cache
}

sealed interface SharedStorageRootSource {
    data class Direct(val uriStr: UriStr) : SharedStorageRootSource
    data class LookupByUser(val userId: UserId) : SharedStorageRootSource
}

sealed interface FilePathModel {
    val fullRelativePath: List<String>

    /**
     * @param userId needs this to
     */
    data class SharedStorageOnly(
        override val fullRelativePath: List<String>,
        val root: SharedStorageRootSource,
    ) : FilePathModel

    data class ExternalOnly(
        override val fullRelativePath: List<String>,
        val externalLocation: ExternalLocation,
    ) : FilePathModel

    data class DualWrite(
        override val fullRelativePath: List<String>,
        val root: SharedStorageRootSource,
        val externalLocation: ExternalLocation,
    ) : FilePathModel {
        fun toSharedStorageOnly() = SharedStorageOnly(fullRelativePath, root)
        fun toExternalOnly() = ExternalOnly(fullRelativePath, externalLocation)
    }
}

