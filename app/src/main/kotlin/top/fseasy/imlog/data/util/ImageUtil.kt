package top.fseasy.imlog.data.util

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
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
    suspend fun readDimension(context: Context, uri: Uri): ImageDimension? =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)
                    ?.use { stream ->
                        syncReadImageDimensionFromStream(stream)
                    }
            } catch (e: Exception) {
                Timber.i(e, "Failed to open input stream from uri")
                null
            }
        }

    /**
     * Read the image boundary from a File.
     * @return null if file not exist or open fail.
     *
     * run in IO thread.
     */
    suspend fun readDimension(file: File): ImageDimension? = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) return@withContext null
            file.inputStream()
                .use { stream ->
                    syncReadImageDimensionFromStream(stream)
                }
        } catch (e: Exception) {
            Timber.i(e, "Failed to open input stream from file")
            null
        }
    }

    /**
     * Core logic to read dimensions from InputStream.
     */
    private fun syncReadImageDimensionFromStream(stream: java.io.InputStream): ImageDimension? {
        return try {
            val options = BitmapFactory.Options()
                .apply {
                    inJustDecodeBounds = true
                }
            BitmapFactory.decodeStream(stream, null, options)
            ImageDimension(options.outWidth, options.outHeight)
        } catch (e: Exception) {
            Timber.i(e, "Failed to decode stream bounds")
            null
        }
    }
}