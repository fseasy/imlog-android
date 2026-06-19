package top.fseasy.imlog.worker;

import android.content.Context;

import androidx.hilt.work.HiltWorker;
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters;
import androidx.work.workDataOf

import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import timber.log.Timber
import top.fseasy.imlog.data.repository.FileManager;
import top.fseasy.imlog.domain.repository.MessageRepository;
import top.fseasy.imlog.util.toFile
import java.time.Duration

@HiltWorker
class DeleteFileWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val fileManager: FileManager,
    private val messageRepository: MessageRepository,
) : CoroutineWorker(context, workerParams) {
    companion object {
        private const val KEY_FILE_PATH = "file_path"
        private const val KEY_MAX_RETRIES = "max_retries"
        private const val DEFAULT_MAX_RETRIES = 3

        fun createRequest(
            filePath: String,
            maxRetries: Int = DEFAULT_MAX_RETRIES,
        ): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<DeleteFileWorker>()
                .setInputData(
                    workDataOf(
                        KEY_FILE_PATH to filePath,
                        KEY_MAX_RETRIES to maxRetries
                    )
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    Duration.ofMinutes(1)
                )
                .build()
        }
    }

    override suspend fun doWork(): Result {
        val file = inputData.getString(KEY_FILE_PATH)
            ?.toFile()
            ?: return Result.failure(workDataOf("error" to "Missing file path"))

        val maxRetries = inputData.getInt(KEY_MAX_RETRIES, DEFAULT_MAX_RETRIES)
        val currentRetryCount = runAttemptCount  // WorkManager 内置的重试计数

        return if (!file.exists()) {
            // 文件已经不存在，视为成功
            Result.success()
        } else {
            val deleted = try {
                file.delete()
            } catch (e: Exception) {
                Timber.i(e, "Delete File worker error")
                false
            }

            if (deleted) {
                Result.success()
            } else if (currentRetryCount >= maxRetries) {
                // 达到最大重试次数，放弃删除; 可记录日志，但不影响主流程
                Result.failure(workDataOf("error" to "Failed to delete after $maxRetries retries"))
            } else {
                Result.retry()
            }
        }
    }
}
