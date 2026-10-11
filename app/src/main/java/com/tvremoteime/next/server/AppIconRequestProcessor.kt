package com.tvremoteime.next.server

import android.content.Context
import com.tvremoteime.next.core.AppPackagesHelper
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream

/**
 * 应用图标端点（对应旧版 AppIconRequestProcesser.java）
 *
 * GET /icon/{packageName} → PNG 图标
 */
class AppIconRequestProcessor(private val context: Context) : RequestProcessor {

    override fun isRequest(session: NanoHTTPD.IHTTPSession, fileName: String): Boolean =
        session.method == NanoHTTPD.Method.GET && fileName.startsWith("/icon/")

    override fun doResponse(
        session: NanoHTTPD.IHTTPSession,
        fileName: String,
        params: Map<String, String>,
        files: Map<String, String>
    ): NanoHTTPD.Response {
        val packageName = fileName.substring("/icon/".length)
        val data = AppPackagesHelper.getAppIcon(packageName, context)
        return if (data == null) {
            RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.NOT_FOUND, "Not Found")
        } else {
            NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK, "image/png",
                ByteArrayInputStream(data), data.size.toLong()
            )
        }
    }
}
