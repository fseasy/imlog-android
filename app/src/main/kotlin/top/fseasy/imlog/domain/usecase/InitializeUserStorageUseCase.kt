package top.fseasy.imlog.domain.usecase

import top.fseasy.imlog.domain.model.StoragePathModel
import top.fseasy.imlog.domain.model.SharedStorageRootSource
import top.fseasy.imlog.domain.model.UriStr
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.ResourceProvider
import top.fseasy.imlog.domain.repository.StorageRepository
import top.fseasy.imlog.domain.repository.StringResId
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private data class DetermineStorageRootUriSuccessResult(
    val rootUriString: UriStr,
    val dirName: String,
)

data class InitializeStorageResult(
    val rootDirName: String,
    val markerFilename: String,
)

class InitializeUserStorageUseCase @Inject constructor(
    private val resourceProvider: ResourceProvider,
    private val storagePathUseCase: StoragePathUseCase,
    private val storageRepository: StorageRepository,
) {

    suspend operator fun invoke(
        userId: UserId,
        selectedUriStr: UriStr,
    ) = runCatching {
        val determinedResult = determineSharedStorageRootUri(selectedUriStr).getOrThrow()
        storageRepository.setSharedStorageRootUriAndUpdateInitData(
            userId, determinedResult.rootUriString
        )
        InitializeStorageResult(
            rootDirName = determinedResult.dirName,
            markerFilename = createSharedStorageRootMarkerFile(userId).getOrThrow()
        )
    }

    /**
     * After user select the storage uri, we should then determine use it directly or
     * create a subdirectory with default root name.
     */
    private suspend fun determineSharedStorageRootUri(
        userSelectedUriStr: UriStr,
    ) = runCatching {
        val dirName = storageRepository.getDisplayNameOrDefault(
            userSelectedUriStr,
            // Don't know what's user selection. Set this to trigger subdirectory creation.
            defaultName = "unknown"
        )
        if (storagePathUseCase.needsSubDirForActualSharedStorageRoot(dirName)) {
            val rootDirName = storagePathUseCase.defaultSharedStorageRootDirName
            val createdUri = storageRepository.mkdirs(
                StoragePathModel.SharedStorageOnly(
                    listOf(rootDirName), root = SharedStorageRootSource.Direct(userSelectedUriStr)
                )
            )
            val effectiveCreatedUri =
                requireNotNull(createdUri) { "mkdir for SharedStorage while no uri returned" }
            DetermineStorageRootUriSuccessResult(effectiveCreatedUri, rootDirName)
        } else {
            DetermineStorageRootUriSuccessResult(userSelectedUriStr, dirName)
        }
    }

    /**
     * Create a file to mark the root of the shared-storage
     * @return Result<String> for marker file name
     */
    private suspend fun createSharedStorageRootMarkerFile(userId: UserId) = runCatching {
        val currentDate = LocalDate.now()
        val formattedDate = currentDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val content = """
            ROOT DIR FOR USER ${userId.value}
            Created by app ${resourceProvider.getString(StringResId.AppName)}
            On $formattedDate
        """.trimIndent()
        val markerPath = storagePathUseCase.buildSharedStorageRootMarkerFilePath(userId)
        storageRepository.writeFile(
            StoragePathModel.SharedStorageOnly(
                markerPath.fullRelativePath, root = SharedStorageRootSource.LookupByUser(userId)
            ),
            content = content.toByteArray(),
            mimeType = "plain/text",
        )
        markerPath.fullRelativePath.last()
    }

}