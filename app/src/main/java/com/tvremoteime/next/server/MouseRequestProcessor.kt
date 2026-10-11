package com.tvremoteime.next.server

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import com.tvremoteime.next.mouse.MouseAccessibilityService
import fi.iki.elonen.NanoHTTPD

/**
 * 触控板端点（移植自 cacaview MouseRequestProcesser.java）
 *
 * POST /mouse/move      {dx, dy}       相对移动
 * POST /mouse/click     {button}       0=左键 1=右键(返回) 2=中键
 * POST /mouse/scroll    {dy}           滚轮（正=向下）
 * POST /mouse/swipeup|swipedown {distance} 上/下划
 * POST /mouse/longclick {duration}     长按
 * POST /mouse/show|hide                光标显隐
 * GET  /mouse/status                   服务状态与光标位置
 */
class MouseRequestProcessor(
    private val context: Context,
    private val remoteServer: RemoteServer
) : RequestProcessor {

    override fun isRequest(session: NanoHTTPD.IHTTPSession, fileName: String): Boolean {
        return if (session.method == NanoHTTPD.Method.POST) {
            fileName in setOf(
                "/mouse/move", "/mouse/click", "/mouse/scroll", "/mouse/swipeup",
                "/mouse/swipedown", "/mouse/longclick", "/mouse/show", "/mouse/hide"
            )
        } else if (session.method == NanoHTTPD.Method.GET) {
            fileName == "/mouse/status"
        } else false
    }

    override fun doResponse(
        session: NanoHTTPD.IHTTPSession,
        fileName: String,
        params: Map<String, String>,
        files: Map<String, String>
    ): NanoHTTPD.Response {
        if (fileName == "/mouse/status") return handleStatus()

        val service = MouseAccessibilityService.getInstance()
        if (service == null) {
            return RemoteServer.createJSONResponse(
                NanoHTTPD.Response.Status.OK,
                "{\"status\":\"error\",\"message\":\"请先在系统设置中启用辅助功能服务\",\"code\":\"accessibility_not_enabled\"}"
            )
        }

        fun ok() = RemoteServer.createJSONResponse(NanoHTTPD.Response.Status.OK, "{\"status\":\"ok\"}")
        fun err(msg: String) = RemoteServer.createJSONResponse(
            NanoHTTPD.Response.Status.OK, "{\"status\":\"error\",\"message\":\"$msg\"}"
        )
        fun needN() = err("需要Android 7.0或更高版本")

        return when (fileName) {
            "/mouse/move" -> {
                val dx = params["dx"]?.toIntOrNull() ?: 0
                val dy = params["dy"]?.toIntOrNull() ?: 0
                val newPos = service.moveMouse(dx, dy)
                RemoteServer.createJSONResponse(
                    NanoHTTPD.Response.Status.OK,
                    "{\"status\":\"ok\",\"x\":${newPos[0]},\"y\":${newPos[1]}}"
                )
            }
            "/mouse/click" -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) needN()
                else {
                    val button = params["button"]?.toIntOrNull() ?: 0
                    if (service.click(button)) ok() else err("click failed")
                }
            }
            "/mouse/scroll" -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) needN()
                else {
                    val dy = params["dy"]?.toIntOrNull() ?: 0
                    if (service.scroll(dy)) ok() else err("scroll failed")
                }
            }
            "/mouse/swipeup" -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) needN()
                else {
                    val distance = (params["distance"]?.toIntOrNull() ?: 300).coerceIn(50, 1000)
                    if (service.swipeUp(distance)) ok() else err("swipe up failed")
                }
            }
            "/mouse/swipedown" -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) needN()
                else {
                    val distance = (params["distance"]?.toIntOrNull() ?: 300).coerceIn(50, 1000)
                    if (service.swipeDown(distance)) ok() else err("swipe down failed")
                }
            }
            "/mouse/longclick" -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) needN()
                else {
                    val duration = (params["duration"]?.toIntOrNull() ?: 600).coerceIn(200, 3000)
                    if (service.longClick(duration)) ok() else err("long click failed")
                }
            }
            "/mouse/show" -> {
                service.showCursor(); ok()
            }
            "/mouse/hide" -> {
                service.hideCursor(); ok()
            }
            else -> RemoteServer.createPlainTextResponse(
                NanoHTTPD.Response.Status.NOT_FOUND, "Error 404, file not found."
            )
        }
    }

    private fun handleStatus(): NanoHTTPD.Response {
        val serviceEnabled = MouseAccessibilityService.isServiceEnabled()
        val service = MouseAccessibilityService.getInstance()

        var mouseX = 0
        var mouseY = 0
        if (service != null) {
            val pos = service.getMousePosition()
            mouseX = pos[0]
            mouseY = pos[1]
        }

        var screenWidth = 0
        var screenHeight = 0
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
        } catch (ignored: Exception) {
        }

        val json = "{\"serviceEnabled\":$serviceEnabled,\"mouseX\":$mouseX,\"mouseY\":$mouseY," +
                "\"screenWidth\":$screenWidth,\"screenHeight\":$screenHeight,\"apiLevel\":${Build.VERSION.SDK_INT}}"
        return RemoteServer.createJSONResponse(NanoHTTPD.Response.Status.OK, json)
    }
}
