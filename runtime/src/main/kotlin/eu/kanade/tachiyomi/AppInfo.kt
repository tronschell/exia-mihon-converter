package eu.kanade.tachiyomi

object AppInfo {
    fun getVersionCode(): Int = 1
    fun getVersionName(): String = "1.7.1"
    fun getSupportedImageMimeTypes(): List<String> = listOf("image/jpeg", "image/png", "image/gif", "image/webp")
}
