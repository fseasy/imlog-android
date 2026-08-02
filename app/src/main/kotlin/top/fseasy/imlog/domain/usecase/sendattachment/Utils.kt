package top.fseasy.imlog.domain.usecase.sendattachment

import top.fseasy.imlog.domain.model.MessageType

/** Helper function to map mimetype to MessageType. Mainly for GenericFile */
fun fileMimeTypeToMessageType(mimeType: String): MessageType =
    if (mimeType.startsWith("video")) {
      MessageType.Video
    } else if (mimeType.startsWith("audio")) {
      MessageType.Audio
    } else if (mimeType.startsWith("image")) {
      MessageType.Image
    } else MessageType.GenericFile
