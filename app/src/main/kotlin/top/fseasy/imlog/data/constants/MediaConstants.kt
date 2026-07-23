package top.fseasy.imlog.data.constants

import top.fseasy.imlog.BuildConfig
import top.fseasy.imlog.domain.model.AppImageFormat

const val TIMELINE_THUMBNAIL_MAX_WIDTH = 540
const val TIMELINE_THUMBNAIL_MAX_HEIGHT = 360

const val TIMELINE_THUMBNAIL_COMPRESS_QUALITY = 80
val TIMELINE_THUMBNAIL_COMPRESS_FORMAT = AppImageFormat.Webp

const val FILE_PROVIDER_AUTHORITIES = "${BuildConfig.APPLICATION_ID}.fileprovider"