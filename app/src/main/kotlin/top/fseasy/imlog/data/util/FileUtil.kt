package top.fseasy.imlog.data.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import top.fseasy.imlog.data.constants.FILE_PROVIDER_AUTHORITIES
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * 将 Uri 指向的内容复制到应用内部存储。
 *
 * @param context to access ContentResolver
 * @throws FileNotFoundException
 * @throws IOException
 */
@Throws(IOException::class)
fun copyUriToFile(context: Context, srcUri: Uri, destination: File): File {
    destination.parentFile?.mkdirs()
    context.contentResolver.openInputStream(srcUri)
        ?.use { input ->
            destination.outputStream()
                .use { output ->
                    input.copyTo(output)
                }
        } ?: throw FileNotFoundException("Can not open file: $srcUri")

    return destination
}

fun File.toFileProviderUri(context: Context): Uri =
    FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITIES, this)
