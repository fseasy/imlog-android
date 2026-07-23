package top.fseasy.imlog.data.util

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.concurrent.futures.await
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.inspector.MetadataRetriever
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.toDuration
import kotlin.use

data class VideoDimension(
    val duration: Duration,
    val width: Int,
    val height: Int,
)

object VideoUtil {

    /**
     * Transform file to FileProvider Uri and then use Media3-inspector to retrieve video metadata
     *
     * Run in IO threads.
     *
     * @throws CancellationException
     */
    suspend fun readDimensionOrNull(context: Context, file: File): VideoDimension? =
        readDimensionOrNull(context, file.toFileProviderUri(context))

    /**
     * Use Media3-inspector to retrieve video metadata
     *
     * Run in IO threads.
     *
     * @throws CancellationException
     */
    @OptIn(UnstableApi::class)
    suspend fun readDimensionOrNull(context: Context, uri: Uri): VideoDimension? {
        return withContext(Dispatchers.IO) {
            try {
                val mediaItem = MediaItem.fromUri(uri)
                MetadataRetriever.Builder(context, mediaItem)
                    .build()
                    .use { retriever ->
                        // 2. .await() transform ListenableFuture to suspend
                        val durationUs = retriever.retrieveDurationUs()
                            .await()
                        val trackGroups = retriever.retrieveTrackGroups()
                            .await()

                        // 3. get w, h from TrackGroup
                        var width = 0
                        var height = 0
                        for (i in 0 until trackGroups.length) {
                            val trackGroup = trackGroups.get(i)
                            for (j in 0 until trackGroup.length) {
                                val format = trackGroup.getFormat(j)
                                if (format.height > 0 && format.width > 0) {
                                    width = format.width
                                    height = format.height
                                    break
                                }
                            }
                        }
                        VideoDimension(durationUs.microseconds, width, height)
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to extract metadata with Media3 MetadataRetriever")
                null
            }
        }
    }
}