package com.tvremoteime.next.server

import android.content.Context
import android.os.Build
import com.tvremoteime.next.control.Capability
import com.tvremoteime.next.control.ControlCenter
import com.tvremoteime.next.mouse.MouseAccessibilityService
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject

class MouseRequestProcessor(private val context: Context, remoteServer: RemoteServer) : RequestProcessor {
    override fun isRequest(session: NanoHTTPD.IHTTPSession, fileName: String): Boolean =
        if (session.method == NanoHTTPD.Method.GET) fileName == "/mouse/status"
        else session.method == NanoHTTPD.Method.POST && fileName in setOf(
            "/mouse/move", "/mouse/click", "/mouse/scroll", "/mouse/swipeup", "/mouse/swipedown",
            "/mouse/longclick", "/mouse/show", "/mouse/hide")

    override fun doResponse(session: NanoHTTPD.IHTTPSession, fileName: String, params: Map<String, String>, files: Map<String, String>): NanoHTTPD.Response {
        ControlCenter.init(context)
        val mouse = ControlCenter.mouse()
        fun value(name: String, default: Int = 0) = params[name]?.toIntOrNull() ?: default
        fun json(data: JSONObject) = RemoteServer.createJSONResponse(NanoHTTPD.Response.Status.OK, data.toString())
        if (fileName == "/mouse/status") {
            val p = mouse.position()
            val channel = ControlCenter.router().pick(Capability.TOUCH)
            return json(JSONObject().put("serviceEnabled", MouseAccessibilityService.isServiceEnabled())
                .put("controlAvailable", channel != null).put("activeChannel", channel?.id ?: JSONObject.NULL)
                .put("mouseX", p[0]).put("mouseY", p[1]).put("screenWidth", p[2]).put("screenHeight", p[3])
                .put("apiLevel", Build.VERSION.SDK_INT))
        }
        if (fileName == "/mouse/move") {
            val p = mouse.move(value("dx"), value("dy"))
            return json(JSONObject().put("status", "ok").put("x", p[0]).put("y", p[1]))
        }
        val service = MouseAccessibilityService.getInstance()
        val success = when (fileName) {
            "/mouse/click" -> mouse.click(value("button"))
            "/mouse/scroll" -> value("dy") == 0 || mouse.swipe(value("dy").toLong() * 50, capability = Capability.SCROLL)
            "/mouse/swipeup" -> mouse.swipe(-value("distance", 300).coerceIn(50, 1000).toLong())
            "/mouse/swipedown" -> mouse.swipe(value("distance", 300).coerceIn(50, 1000).toLong())
            "/mouse/longclick" -> mouse.swipe(0, value("duration", 600).coerceIn(200, 3000))
            "/mouse/show" -> { service?.showCursor(); service != null }
            "/mouse/hide" -> { service?.hideCursor(); service != null }
            else -> false
        }
        return json(JSONObject().put("status", if (success) "ok" else "error").apply {
            if (!success) put("message", "No available control channel; enable accessibility, Root or ADB")
        })
    }
}
