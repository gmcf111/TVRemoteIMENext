package com.tvremoteime.next.server

import android.content.Context
import com.tvremoteime.next.core.VideoPlayHelper
import fi.iki.elonen.NanoHTTPD
import xllib.DownloadTask
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * 种子边下边播端点（对应旧版 TorrentRequestProcesser.java）
 *
 * POST /torrent/upload → 上传种子文件
 * POST /torrent/play {videoIndex, useSystem} → 播放种子内视频
 * GET/POST /torrent → 返回种子文件列表
 */
class TorrentRequestProcessor(private val context: Context) : RequestProcessor {

    private val downloadTask = DownloadTask()

    override fun isRequest(session: NanoHTTPD.IHTTPSession, fileName: String): Boolean {
        if (session.method == NanoHTTPD.Method.POST) {
            return when (fileName) {
                "/torrent/data", "/torrent/upload", "/torrent/play" -> true
                else -> false
            }
        }
        return fileName.equals("/torrent", ignoreCase = true)
    }

    override fun doResponse(
        session: NanoHTTPD.IHTTPSession,
        fileName: String,
        params: Map<String, String>,
        files: Map<String, String>
    ): NanoHTTPD.Response {
        if (session.method == NanoHTTPD.Method.POST) {
            when (fileName) {
                "/torrent/upload" -> saveTorrent(params, files)
                "/torrent/play" -> return RemoteServer.createPlainTextResponse(
                    NanoHTTPD.Response.Status.OK, if (playTorrent(params)) "ok" else "fail"
                )
            }
        }
        return if (ServerFileManager.getPlayTorrentFile().exists()) {
            responseTorrentFileList()
        } else {
            RemoteServer.createJSONResponse(NanoHTTPD.Response.Status.OK, "{\"success\":false}")
        }
    }

    private fun saveTorrent(params: Map<String, String>, files: Map<String, String>) {
        val uploadFileName = params["file"]
        val localFilename = files["file"]
        if (!uploadFileName.isNullOrEmpty() && !localFilename.isNullOrEmpty()) {
            val localFile = File(localFilename)
            localFile.renameTo(ServerFileManager.getPlayTorrentFile())
        }
    }

    private fun responseTorrentFileList(): NanoHTTPD.Response {
        downloadTask.url = ServerFileManager.getPlayTorrentFile().absolutePath
        val data = JSONObject()
        try {
            data.put("success", true)
            val items = JSONArray()
            for (item in downloadTask.playList) {
                val playItem = JSONObject()
                playItem.put("name", item.name)
                playItem.put("index", item.index)
                playItem.put("size", item.size)
                items.put(playItem)
            }
            data.put("files", items)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return RemoteServer.createJSONResponse(NanoHTTPD.Response.Status.OK, data.toString())
    }

    private fun playTorrent(params: Map<String, String>): Boolean {
        val torrentFile = ServerFileManager.getPlayTorrentFile()
        if (!torrentFile.exists()) return false
        val videoIndex = params["videoIndex"]?.toIntOrNull() ?: 0
        VideoPlayHelper.playUrl(context, torrentFile.absolutePath, videoIndex, params["useSystem"] == "true")
        return true
    }
}
