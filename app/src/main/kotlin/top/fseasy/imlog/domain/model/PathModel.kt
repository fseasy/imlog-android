package top.fseasy.imlog.domain.model

import kotlinx.serialization.Serializable

/** The uri representation in Domain level, without introducing the android platform dependency */
@JvmInline
@Serializable
value class UriStr(val value: String)

/**
 * Represents a file path that the app can directly access via normal file APIs (e.g.
 * context.filesDir, cacheDir, etc.).
 *
 * Using String is sufficient at the domain level.
 */
@JvmInline @Serializable value class AppPath(val value: String)

@Serializable
sealed interface SharedStorageRootSource {
  @Serializable
  data class Direct(val uriStr: UriStr) : SharedStorageRootSource

  @Serializable
  data class LookupByUser(val userId: UserId) : SharedStorageRootSource
}

/** To flag it is in internal-persistent or internal-cache location */
@Serializable
enum class InternalStorageRootSource {
  Persistent,
  Cache,
}

/**
 * Platform-agnostic storage path model. Use it
 * - in Domain level
 * - in db: avoid storage-media migration that happens in legacy android.
 *
 * @see top.fseasy.imlog.domain.usecase.StoragePathUseCase to know the storage choose reason
 * @see top.fseasy.imlog.data.mapper StoragePathModelMapper.kt
 */
@Serializable
sealed interface StoragePathModel {
  val fullRelativePath: List<String>

  @Serializable
  data class Shared(
      override val fullRelativePath: List<String>,
      val root: SharedStorageRootSource,
  ) : StoragePathModel

  @Serializable
  data class Internal(
      override val fullRelativePath: List<String>,
      val root: InternalStorageRootSource,
  ) : StoragePathModel

  @Serializable
  data class Dual(
      override val fullRelativePath: List<String>,
      val sharedRoot: SharedStorageRootSource,
      val internalRoot: InternalStorageRootSource,
  ) : StoragePathModel {
    fun toShared() = Shared(fullRelativePath, sharedRoot)

    fun toInternal() = Internal(fullRelativePath, internalRoot)
  }
}

/**
 * should be used for UI/Data layer.
 *
 * @see top.fseasy.imlog.data.mapper AbsolutePathModelMapper.kt
 * @StoragePathModel can be transformed to this type, with platform specific context.
 */
sealed interface AbsolutePathModel {
  data class UriStrModel(val value: UriStr) : AbsolutePathModel

  data class AppPathModel(val value: AppPath) : AbsolutePathModel
}
