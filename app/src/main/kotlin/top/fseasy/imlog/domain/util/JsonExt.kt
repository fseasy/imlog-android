package top.fseasy.imlog.domain.util

import kotlinx.serialization.json.Json

val defaultJson = Json {
  ignoreUnknownKeys = true
  encodeDefaults = true
}
