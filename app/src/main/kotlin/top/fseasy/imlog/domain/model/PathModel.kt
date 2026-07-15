package top.fseasy.imlog.domain.model

import java.io.File

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
 * Used to fit the clean architecture requirements, mainly used as output stream.
 * @see top.fseasy.imlog.domain.usecase.StoragePathUseCase to know the storage choose reason
 * @see top.fseasy.imlog.data.mapper.getMimeType*
 */
sealed interface StoragePathModel {
    val fullRelativePath: List<String>

    data class SharedStorageOnly(
        override val fullRelativePath: List<String>,
        val root: SharedStorageRootSource,
    ) : StoragePathModel

    data class InternalOnly(
        override val fullRelativePath: List<String>,
        val internalLocation: InternalLocation,
    ) : StoragePathModel

    data class DualWrite(
        override val fullRelativePath: List<String>,
        val root: SharedStorageRootSource,
        val internalLocation: InternalLocation,
    ) : StoragePathModel {
        fun toSharedStorageOnly() = SharedStorageOnly(fullRelativePath, root)
        fun toInternalOnly() = InternalOnly(fullRelativePath, internalLocation)
    }
}

/**
 * Mainly Used in file handling result, or input file.
 * @StoragePathModel can be transformed to this type, with platform specific context.
 * @see top.fseasy.imlog.data.repository.StorageRepositoryImpl
 */
sealed interface AbsolutePathModel {
    data class UriStrModel(val value: UriStr) : AbsolutePathModel
    data class FileModel(val value: File) : AbsolutePathModel
}
