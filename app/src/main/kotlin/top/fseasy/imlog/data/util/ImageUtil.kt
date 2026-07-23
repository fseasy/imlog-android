package top.fseasy.imlog.data.util

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

data class ImageDimension(val width: Int, val height: Int)


object ImageUtil {
    /**
     * Read the image boundary without decoding the whole image. It's high efficiency.
     * @return null if uri invalid or open fail.
     *
     * run in IO threads.
     */
    suspend fun readDimensionOrNull(context: Context, uri: Uri): ImageDimension? =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)
                    ?.use { stream ->
                        syncReadImageDimensionFromStream(stream)
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.i(e, "Failed to read dimension")
                null
            }
        }

    /**
     * Read the image boundary from a File.
     * @return null if file not exist or open fail.
     *
     * run in IO thread.
     */
    suspend fun readDimensionOrNull(file: File): ImageDimension? = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) return@withContext null
            file.inputStream()
                .use { stream ->
                    syncReadImageDimensionFromStream(stream)
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.i(e, "Failed to read dimension")
            null
        }
    }

    /**
     * Core logic to read dimensions from InputStream.
     * @throws IllegalArgumentException from decodeStream
     */
    private fun syncReadImageDimensionFromStream(stream: java.io.InputStream): ImageDimension {
        // No need to try as upper will handle it.
        val options = BitmapFactory.Options()
            .apply {
                inJustDecodeBounds = true
            }
        BitmapFactory.decodeStream(stream, null, options)
        return ImageDimension(options.outWidth, options.outHeight)
    }
}