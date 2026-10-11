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
            context.resources.openRawResource(resourceId).use { input ->
                NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.OK,
                    "$mimeType; charset=utf-8",
                    input,
                    input.available().toLong()
                )
            }
        } catch (e: IOException) {
            RemoteServer.createPlainTextResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "SERVER INTERNAL ERROR: IOException: ${e.message}"
            )
        }
    }
}
