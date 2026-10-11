package com.tvremoteime.next.server

import android.content.Context
import com.tvremoteime.next.theme.ThemePrefs
import fi.iki.elonen.NanoHTTPD

/**
 * 主题色端点（04 文档 5.4，阶段 5 新增）
 *
 * GET  /theme → {"accentId":"blue","primary":"#1565C0","custom":false}
 * POST /theme {accentId} 或 {hex} → 持久化；盒子端下次启动跟随
 */
class ThemeRequestProcessor(private val context: Context) : RequestProcessor {

    override fun isRequest(session: NanoHTTPD.IHTTPSession, fileName: String): Boolean =
        fileName.equals("/theme", ignoreCase = true)

    override fun doResponse(
        session: NanoHTTPD.IHTTPSession,
        fileName: String,
        params: Map<String, String>,
        files: Map<String, String>
    ): NanoHTTPD.Response {
        return if (session.method == NanoHTTPD.Method.POST) {
            val accentId = params["accentId"]
            val hex = params["hex"]
            when {
                !accentId.isNullOrEmpty() && ThemePrefs.presetById(accentId) != null -> {
                    ThemePrefs.setAccent(context, accentId)
                    ok()
                }
                !hex.isNullOrEmpty() && ThemePrefs.parseHex(hex) != null -> {
                    ThemePrefs.setAccent(context, hex)
                    ok()
                }
                hex == "" || accentId == "none" -> {
                    // 清除选择，恢复跟随系统
                    ThemePrefs.clearAccent(context)
                    ok()
                }
                else -> RemoteServer.createJSONResponse(
                    NanoHTTPD.Response.Status.BAD_REQUEST,
                    "{\"status\":\"error\",\"message\":\"invalid accentId or hex\"}"
                )
            }
        } else {
            RemoteServer.createJSONResponse(
                NanoHTTPD.Response.Status.OK,
                com.tvremoteime.next.theme.ThemeHelper.themeJson(context)
            )
        }
    }

    private fun ok() = RemoteServer.createJSONResponse(NanoHTTPD.Response.Status.OK, "{\"status\":\"ok\"}")
}
