package top.fseasy.imlog.domain.util

import kotlinx.serialization.json.Json

internal val defaultJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}