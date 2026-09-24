package top.fseasy.imlog.features.home.topiclog

import top.fseasy.imlog.domain.model.MessageId

fun toMediaInputId(messageId: MessageId) = messageId.value

fun toSharedTransitionElementId(messageId: MessageId) = messageId.value

/** Used for Coil async memory reuse */
fun toMemoryCacheKey(messageId: MessageId) = messageId.value
