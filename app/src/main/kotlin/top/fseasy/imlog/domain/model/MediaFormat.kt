package top.fseasy.imlog.domain.model

/**
 * Use `App` prefix to avoid name confliction with system
 */
enum class AppImageFormat(val filenameSuffix: String, val mimeType: String) {
    Webp(".webp", "image/webp"), Jpeg(".jpg", "image/jpeg"), Png(".png", "image/png");
}
