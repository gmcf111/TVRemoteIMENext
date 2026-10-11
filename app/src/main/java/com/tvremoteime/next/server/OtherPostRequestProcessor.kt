package com.tvremoteime.next.server

import android.content.Context
import fi.iki.elonen.NanoHTTPD

/**
 * 杂项 POST 端点（对应旧版 OtherPostRequestProcesser.java）
 *
 * POST /clearCache → 清空服务缓存目录
 */
class OtherPostRequestProcessor(private val context: Context) : RequestProcessor {

    override fun isRequest(session: NanoHTTPD.IHTTPSession, fileName: String): Boolean =
        session.method == NanoHTTPD.Method.POST && fileName == "/clearCache"

    override fun doResponse(
        session: NanoHTTPD.IHTTPSession,
        fileName: String,
        params: Map<String, String>,
        files: Map<String, String>
    ): NanoHTTPD.Response = when (fileName) {
        "/clearCache" -> {
            ServerFileManager.clearAllFiles()
            RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.OK, "ok")
        }
        else -> RemoteServer.createPlainTextResponse(
            NanoHTTPD.Response.Status.NOT_FOUND, "Error 404, file not found."
        )
    }
}
