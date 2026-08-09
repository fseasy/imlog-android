package top.fseasy.imlog.ui.util

import top.fseasy.imlog.R

fun mimeTypeToIconResId(mimeType: String?): Int {
  if (mimeType.isNullOrBlank()) {
    return R.drawable.icon_file_present
  }

  return when {
    mimeType.startsWith("image/") -> R.drawable.icon_image

    mimeType.startsWith("video/") -> R.drawable.icon_video_file

    mimeType.isAudioMime() -> getAudioIconResId(mimeType)
    mimeType == "application/pdf" -> R.drawable.icon_file_pdf

    mimeType == "application/msword" ||
        mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
        R.drawable.icon_description

    mimeType == "application/vnd.ms-excel" ||
        mimeType == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ->
        R.drawable.icon_table

    mimeType == "application/vnd.ms-powerpoint" ||
        mimeType == "application/vnd.openxmlformats-officedocument.presentationml.presentation" ->
        R.drawable.icon_slideshow

    mimeType == "application/zip" ||
        mimeType == "application/x-zip-compressed" ||
        mimeType == "application/x-rar-compressed" ||
        mimeType == "application/vnd.rar" ||
        mimeType == "application/x-7z-compressed" ||
        mimeType == "application/gzip" ||
        mimeType == "application/x-tar" -> R.drawable.icon_archive

    mimeType == "application/vnd.android.package-archive" -> R.drawable.icon_apk_document

    mimeType.startsWith("text/") ||
        mimeType == "application/json" ||
        mimeType == "application/xml" ||
        mimeType == "application/javascript" ||
        mimeType == "application/x-javascript" -> R.drawable.icon_description

    else -> R.drawable.icon_file_present
  }
}

private fun getAudioIconResId(mimeType: String): Int {
  return when (mimeType) {
    "audio/x-mpegurl",
    "audio/x-scpls",
    "application/vnd.apple.mpegurl" -> {
      R.drawable.icon_library_music
    }

    "audio/flac",
    "audio/x-flac",
    "audio/alac",
    "audio/ape" -> {
      R.drawable.icon_album
    }

    "audio/amr",
    "audio/3gpp",
    "audio/opus" -> {
      R.drawable.icon_audio_capture
    }

    else -> R.drawable.icon_audio_file
  }
}

private fun String.isAudioMime(): Boolean {
  return startsWith("audio/") ||
      this in
          setOf(
              "application/ogg",
              "application/x-mpegurl", // m3u list
              "application/vnd.apple.mpegurl",
          )
}
