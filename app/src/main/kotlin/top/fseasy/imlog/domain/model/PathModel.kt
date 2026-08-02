package top.fseasy.imlog.domain.model

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * The uri representation in Domain level, without introducing the android platform dependency
 */
@JvmInline
@Serializable
value class UriStr(val value: String) {
    init {
        require(value.isNotBlank()) { "URI string cannot be blank" }
    }
}

/**
 * Represents a file path that the app can directly access via normal file APIs
 * (e.g. context.filesDir, cacheDir, etc.).
 *
 * Using String is sufficient at the domain level.
 */
@JvmInline
@Serializable
value class AppPath(val value: String)

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
 * Used in condition that needs an output paths.
 * Use this way to obey the clean architecture requirements.
 * Limit this class usage in Domain layer and StorageRepository.
 * @see top.fseasy.imlog.domain.usecase.StoragePathUseCase to know the storage choose reason
 * @see top.fseasy.imlog.data.mapper StoragePathModelMapper.kt
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
 * In domain layer, it's used in user-given file condition, or file processing result.
 * In data/UI layer, should prefer use this type instead of @StoragePathModel.
 * @StoragePathModel can be transformed to this type, with platform specific context.
 *
 * @see top.fseasy.imlog.data.mapper AbsolutePathModelMapper.kt
 */
sealed interface AbsolutePathModel {
    data class UriStrModel(val value: UriStr) : AbsolutePathModel
    data class AppPathModel(val value: AppPath) : AbsolutePathModel
}
