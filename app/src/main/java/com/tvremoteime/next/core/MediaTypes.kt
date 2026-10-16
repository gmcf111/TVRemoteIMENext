package com.tvremoteime.next.core

/**
 * 媒体文件类型判定（v3.0）
 *
 * 3.0 移除迅雷/种子支持后，app 侧不再依赖 ijkplayer 模块的 xllib.FileUtils，
 * 将"是否可播放媒体"这一判定收归应用层，供文件管理、上传自动播放复用。
 */
object MediaTypes {

    private val MEDIA_EXTENSIONS = setOf(
        ".avi", ".mp4", ".m4v", ".mkv", ".mov", ".mpeg", ".mpg", ".mpe",
        ".rm", ".rmvb", ".3gp", ".wmv", ".asf", ".asx", ".dat", ".vob", ".m3u8"
    )

    /** 扩展名（含点，小写）；无扩展名返回空串 */
    fun extensionOf(fileName: String?): String {
        if (fileName.isNullOrEmpty()) return ""
        val index = fileName.lastIndexOf('.')
        return if (index == -1) "" else fileName.substring(index).lowercase()
    }

    /** 是否为可播放的媒体文件 */
    fun isMediaFile(fileName: String?): Boolean = extensionOf(fileName) in MEDIA_EXTENSIONS
}
