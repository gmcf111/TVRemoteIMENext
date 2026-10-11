package com.tvremoteime.next.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import player.XLVideoPlayActivity
import xllib.DownloadManager

/**
 * 视频播放助手（对应旧版 VideoPlayHelper.java）
 */
object VideoPlayHelper {
    fun playUrl(context: Context, url: String, videoIndex: Int, useSystem: Boolean) {
        if (useSystem) {
            // 外部播放：先经迅雷任务转换为可播地址（边下边播），再调系统播放器
            val downloadManager = DownloadManager.instance()
            downloadManager.init(context)
            downloadManager.taskInstance().setUrl(url)
            if (videoIndex > 0) downloadManager.taskInstance().changePlayItem(videoIndex)
            downloadManager.taskInstance().startTask()
            val playUrl = downloadManager.taskInstance().playUrl
            if (!TextUtils.isEmpty(playUrl)) {
                Env.debug("外部调用播放视频, playUrl=$playUrl, video/*")
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(playUrl), "video/*")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        } else {
            // 内部播放
            XLVideoPlayActivity.intentTo(XLVideoPlayActivity::class.java, context, url, url, videoIndex)
        }
    }
}
