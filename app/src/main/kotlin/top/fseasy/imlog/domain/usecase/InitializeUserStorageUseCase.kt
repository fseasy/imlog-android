package top.fseasy.imlog.domain.usecase

import top.fseasy.imlog.domain.model.UriStr
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.ResourceProvider
import top.fseasy.imlog.domain.repository.StorageRepository
import top.fseasy.imlog.domain.repository.StringConstantId
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
    private val appStaticName = resourceProvider.getConstString(StringConstantId.AppStaticName)
    private val defaultSharedStorageRootDirName = "${appStaticName}-storage"

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
        val dirName = storageRepository.getDisplayNameOrThrow(userSelectedUriStr)
        if (shouldCreateSubDirOnUserSelectedDir(dirName)) {
            val rootDirName = defaultSharedStorageRootDirName
            val determinedRootUriName = storageRepository.mkdirsForSharedStorageUri(
                subDirs = listOf(rootDirName), rootUriStr = userSelectedUriStr,
            )
            DetermineStorageRootUriSuccessResult(determinedRootUriName, rootDirName)
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
        val markerFilename = "${appStaticName}.txt"

        storageRepository.writeFileBasedOnUserSharedStorageRoot(
            userId = userId,
            relativePaths = listOf(storagePathUseCase.getUserRootDirName(userId), markerFilename),
            mimeType = "plain/text",
            content = content.toByteArray()
        )
        markerFilename
    }

    private fun shouldCreateSubDirOnUserSelectedDir(selectedDirName: String): Boolean {
        return selectedDirName.contains(appStaticName, ignoreCase = true)
    }
}