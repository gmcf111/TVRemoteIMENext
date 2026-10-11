package com.tvremoteime.next.server

import android.content.Context
import com.tvremoteime.next.control.ControlCenter
import com.tvremoteime.next.control.RootPrefs
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream

/**
 * 截屏端点（02 文档 5.3，阶段 4 新增，root 限定）
 *
 * GET /screenshot → image/png（root 不可用时 403 + JSON 说明）
 */
class ScreenshotRequestProcessor(private val context: Context) : RequestProcessor {

    override fun isRequest(session: NanoHTTPD.IHTTPSession, fileName: String): Boolean =
        session.method == NanoHTTPD.Method.GET && fileName == "/screenshot"

    override fun doResponse(
        session: NanoHTTPD.IHTTPSession,
        fileName: String,
        params: Map<String, String>,
        files: Map<String, String>
    ): NanoHTTPD.Response {
        ControlCenter.init(context)
        val root = ControlCenter.root()
        if (root == null || !RootPrefs.isEnabled(context)) {
            return RemoteServer.createJSONResponse(
                NanoHTTPD.Response.Status.FORBIDDEN,
                "{\"status\":\"error\",\"code\":\"root_disabled\",\"message\":\"未开启 Root 增强，请在应用主页开启\"}"
            )
        }
        if (!root.isAvailable()) {
            return RemoteServer.createJSONResponse(
                NanoHTTPD.Response.Status.FORBIDDEN,
                "{\"status\":\"error\",\"code\":\"root_unavailable\",\"message\":\"su 不可用（未授权或无 root）\"}"
            )
        }
        val data = root.screenshot()
        return if (data == null) {
            RemoteServer.createJSONResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "{\"status\":\"error\",\"code\":\"screenshot_failed\",\"message\":\"screencap 执行失败\"}"
            )
        } else {
            NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK, "image/png",
                ByteArrayInputStream(data), data.size.toLong()
            )
        }
    }
}
