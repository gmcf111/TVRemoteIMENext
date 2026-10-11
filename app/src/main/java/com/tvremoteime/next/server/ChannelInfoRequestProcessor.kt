package com.tvremoteime.next.server

import android.content.Context
import com.tvremoteime.next.control.ControlCenter
import fi.iki.elonen.NanoHTTPD

/**
 * 通道状态端点（02 文档 2.4，阶段 4 新增）
 *
 * GET /channel-info → 四通道可用性与各能力域激活通道（Web 端徽标/引导用）
 */
class ChannelInfoRequestProcessor(private val context: Context) : RequestProcessor {

    override fun isRequest(session: NanoHTTPD.IHTTPSession, fileName: String): Boolean =
        session.method == NanoHTTPD.Method.GET && fileName == "/channel-info"

    override fun doResponse(
        session: NanoHTTPD.IHTTPSession,
        fileName: String,
        params: Map<String, String>,
        files: Map<String, String>
    ): NanoHTTPD.Response {
        ControlCenter.init(context)
        return RemoteServer.createJSONResponse(
            NanoHTTPD.Response.Status.OK,
            ControlCenter.channelInfoJson(context)
        )
    }
}
