package top.fseasy.imlog.features.home.topiclog

import top.fseasy.imlog.domain.model.MessageId

fun toMediaInputId(messageId: MessageId) = messageId.value

/** For OverlayLayout */
fun toOverlayItemKey(messageId: MessageId) = messageId.value

/** Used for Coil async memory reuse */
fun toMemoryCacheKey(messageId: MessageId) = messageId.value
