package com.tvremoteime.next.server

import android.content.Context
import com.tvremoteime.next.core.VideoPlayHelper
import fi.iki.elonen.NanoHTTPD
import player.settings.GlobalSettings
import xllib.DownloadManager

/**
 * 播放控制端点（对应旧版 PlayRequestProcesser.java）
 *
 * POST /play {playUrl, useSystem}
 * POST /playStop
 * POST /changePlayFFI {speedInterval}
 */
class PlayRequestProcessor(private val context: Context) : RequestProcessor {

    override fun isRequest(session: NanoHTTPD.IHTTPSession, fileName: String): Boolean {
        if (session.method != NanoHTTPD.Method.POST) return false
        return when (fileName) {
            "/play", "/playStop", "/changePlayFFI" -> true
            else -> false
        }
    }

    override fun doResponse(
        session: NanoHTTPD.IHTTPSession,
        fileName: String,
        params: Map<String, String>,
        files: Map<String, String>
    ): NanoHTTPD.Response {
        val ok = RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.OK, "ok")
        return when (fileName) {
            "/play" -> {
                val playUrl = params["playUrl"]
                if (!playUrl.isNullOrEmpty()) {
                    VideoPlayHelper.playUrl(context, playUrl, 0, params["useSystem"] == "true")
                }
                ok
            }
            "/playStop" -> {
                DownloadManager.instance().taskInstance().stopTask()
                ok
            }
            "/changePlayFFI" -> {
                params["speedInterval"]?.let {
                    it.toIntOrNull()?.let { n -> GlobalSettings.FastForwardInterval = n * 1000 }
                }
                ok
            }
            else -> RemoteServer.createPlainTextResponse(
                NanoHTTPD.Response.Status.NOT_FOUND, "Error 404, file not found."
            )
        }
    }
}
