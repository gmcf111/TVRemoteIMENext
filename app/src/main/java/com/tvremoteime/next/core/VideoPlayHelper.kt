package com.tvremoteime.next.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import player.XLVideoPlayActivity
import java.io.File

/**
 * 视频播放助手（对应旧版 VideoPlayHelper.java）
 *
 * v3.0：移除迅雷下载与边下边播后，地址不再经下载任务转换，直接交由播放器播放。
 * 支持本地文件、http(s) 直链、rtmp/rtsp/mms 直播流。
 */
object VideoPlayHelper {

    /** 不支持播放的地址 scheme（原迅雷生态，3.0 起不再支持） */
    private val UNSUPPORTED_SCHEMES = listOf("thunder://", "ed2k://", "magnet:?")

    fun playUrl(context: Context, url: String, useSystem: Boolean) {
        if (!isPlayable(url)) {
            Env.debug("不支持的播放地址，已忽略: $url")
            return
        }
        if (useSystem) {
            // 外部播放：直接交给系统播放器
            Env.debug("外部调用播放视频, playUrl=$url, video/*")
            val intent = Intent(Intent.ACTION_VIEW).apply {
                val parsed = Uri.parse(url)
                val uri = if (parsed.scheme == null || parsed.scheme == "file") {
                    val path = parsed.path ?: url
                    androidx.core.content.FileProvider.getUriForFile(
                        context, "${context.packageName}.fileprovider", File(path)
                    )
                } else parsed
                setDataAndType(uri, "video/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } else {
            // 内部播放
            XLVideoPlayActivity.intentTo(XLVideoPlayActivity::class.java, context, url, url, 0)
        }
    }

    /** 是否为可播放地址（非迅雷/种子类地址） */
    fun isPlayable(url: String?): Boolean {
        if (TextUtils.isEmpty(url)) return false
        val lower = url!!.lowercase()
        return UNSUPPORTED_SCHEMES.none { lower.startsWith(it) } && !lower.endsWith(".torrent")
    }
}
