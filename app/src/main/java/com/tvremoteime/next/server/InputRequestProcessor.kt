package com.tvremoteime.next.server

import android.content.Context
import fi.iki.elonen.NanoHTTPD

/**
 * 按键/文字输入端点（对应旧版 InputRequestProcesser.java）
 *
 * POST /text  {text}       → 文字输入
 * POST /key   {code}       → 按键（按下并抬起）
 * POST /keydown /keyup {code} → 按键按下/抬起（游戏类长按场景）
 */
class InputRequestProcessor(
    private val context: Context,
    private val remoteServer: RemoteServer
) : RequestProcessor {

    override fun isRequest(session: NanoHTTPD.IHTTPSession, fileName: String): Boolean {
        if (session.method != NanoHTTPD.Method.POST) return false
        return when (fileName) {
            "/text", "/key", "/keydown", "/keyup" -> true
            else -> false
        }
    }

    override fun doResponse(
        session: NanoHTTPD.IHTTPSession,
        fileName: String,
        params: Map<String, String>,
        files: Map<String, String>
    ): NanoHTTPD.Response {
        val receiver = remoteServer.dataReceiver
        when (fileName) {
            "/text" -> params["text"]?.let { receiver?.onTextReceived(it) }
            "/key" -> params["code"]?.let { receiver?.onKeyEventReceived(it, KEY_ACTION_PRESSED) }
            "/keyup" -> params["code"]?.let { receiver?.onKeyEventReceived(it, KEY_ACTION_UP) }
            "/keydown" -> params["code"]?.let { receiver?.onKeyEventReceived(it, KEY_ACTION_DOWN) }
        }
        return RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.OK, "ok")
    }

    companion object {
        const val KEY_ACTION_PRESSED = 0
        const val KEY_ACTION_DOWN = 1
        const val KEY_ACTION_UP = 2
    }
}
