package top.fseasy.imlog.data.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import top.fseasy.imlog.data.constants.FILE_PROVIDER_AUTHORITIES
import java.io.File

fun File.toFileProviderUri(context: Context): Uri =
    FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITIES, this)
