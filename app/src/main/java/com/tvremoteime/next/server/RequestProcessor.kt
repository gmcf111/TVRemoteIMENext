package com.tvremoteime.next.server

import fi.iki.elonen.NanoHTTPD

/**
 * 请求处理器接口（对应旧版 RequestProcesser.java）
 * 路由注册表化：处理器在 RemoteServer 中按声明顺序匹配（02 文档架构）。
 */
interface RequestProcessor {
    fun isRequest(session: NanoHTTPD.IHTTPSession, fileName: String): Boolean
    fun doResponse(
        session: NanoHTTPD.IHTTPSession,
        fileName: String,
        params: Map<String, String>,
        files: Map<String, String>
    ): NanoHTTPD.Response
}
