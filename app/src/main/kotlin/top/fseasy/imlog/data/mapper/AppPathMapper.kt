package top.fseasy.imlog.data.mapper

import top.fseasy.imlog.domain.model.AppPath
import java.io.File
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

/**
 * To java.io.File
 */
fun AppPath.toFile() = File(value)

/**
 * To nio path
 * @throws InvalidPathException if the string can't be converted to Path.
 */
fun AppPath.toNioPath() = Paths.get(value)

fun File.toAppPath() = AppPath(absolutePath)

fun java.nio.file.Path.toAppPath() = AppPath(absolutePathString())
