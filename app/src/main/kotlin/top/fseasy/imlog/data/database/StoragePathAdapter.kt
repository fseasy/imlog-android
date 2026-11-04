package top.fseasy.imlog.data.database

import app.cash.sqldelight.ColumnAdapter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import top.fseasy.imlog.domain.model.SharedStorageRootSource
import top.fseasy.imlog.domain.model.StoragePathModel
import top.fseasy.imlog.domain.model.UriStr
import top.fseasy.imlog.domain.util.defaultJson

@Serializable
private data class StoragePathEnvelope(
    val version: Int = CURRENT_VERSION,
    val payload: StoragePathModel,
) {
  companion object {
    const val CURRENT_VERSION = 1
  }
}

val storagePathAdapter =
    object : ColumnAdapter<StoragePathModel, String> {

      override fun encode(value: StoragePathModel): String {
        // 编码时自动包裹版本号
        val envelope =
            StoragePathEnvelope(
                version = StoragePathEnvelope.CURRENT_VERSION,
                payload = value,
            )
        return defaultJson.encodeToString(StoragePathEnvelope.serializer(), envelope)
      }

      override fun decode(databaseValue: String): StoragePathModel = runCatching {
        // 优雅处理版本分发：先解析 JSON 树获取版本
        val jsonElement = defaultJson.parseToJsonElement(databaseValue)
        val version = jsonElement.jsonObject["version"]?.jsonPrimitive?.intOrNull ?: 1

        return when (version) {
          1 -> {
            val envelope =
                defaultJson.decodeFromString(StoragePathEnvelope.serializer(), databaseValue)
            envelope.payload
          }
          else -> error("Unsupported StoragePathModel version: $version")
        }
      }
          .getOrElse {
            // Fallback to a dirty value.
            StoragePathModel.Shared(
                fullRelativePath = listOf(),
                root = SharedStorageRootSource.Direct(UriStr(databaseValue)),
            )
          }
    }
