package com.tvremoteime.next.server

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import java.io.IOException

/**
 * 内置静态资源输出（对应旧版 RawRequestProcesser.java）
 */
class RawRequestProcessor(
    private val context: Context,
    private val fileName: String,
    private val resourceId: Int,
    private val mimeType: String
) : RequestProcessor {

    override fun isRequest(session: NanoHTTPD.IHTTPSession, fileName: String): Boolean =
        session.method == NanoHTTPD.Method.GET && this.fileName.equals(fileName, ignoreCase = true)

    override fun doResponse(
        session: NanoHTTPD.IHTTPSession,
        fileName: String,
        params: Map<String, String>,
        files: Map<String, String>
    ): NanoHTTPD.Response {
        return try {
            // Response 在 serve 返回后才发送并关闭流，不能在这里 use/close。
            val input = context.resources.openRawResource(resourceId)
            NanoHTTPD.newChunkedResponse(NanoHTTPD.Response.Status.OK, mimeType, input)
        } catch (e: IOException) {
            RemoteServer.createPlainTextResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "SERVER INTERNAL ERROR: IOException: ${e.message}"
            )
        }
    }
}
