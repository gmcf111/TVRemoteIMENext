package com.tvremoteime.next.server

import android.content.Context
import com.tvremoteime.next.core.AppPackagesHelper
import fi.iki.elonen.NanoHTTPD

/**
 * 应用管理端点（对应旧版 AppRequestProcesser.java）
 *
 * POST /apps {system}         → 应用列表
 * POST /uninstall {packageName}
 * POST /run {packageName}
 * POST /runSystem {packageName}
 */
class AppRequestProcessor(private val context: Context) : RequestProcessor {

    override fun isRequest(session: NanoHTTPD.IHTTPSession, fileName: String): Boolean {
        if (session.method != NanoHTTPD.Method.POST) return false
        return when (fileName) {
            "/apps", "/uninstall", "/run", "/runSystem" -> true
            else -> false
        }
    }

    override fun doResponse(
        session: NanoHTTPD.IHTTPSession,
        fileName: String,
        params: Map<String, String>,
        files: Map<String, String>
    ): NanoHTTPD.Response {
        val ok = RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.OK, "ok")
        return when (fileName) {
            "/apps" -> RemoteServer.createJSONResponse(
                NanoHTTPD.Response.Status.OK,
                AppPackagesHelper.getQueryAppInfoJsonString(context, params["system"] == "true")
            )
            "/uninstall" -> {
                params["packageName"]?.let { AppPackagesHelper.uninstallPackage(it, context) }
                ok
            }
            "/run" -> {
                params["packageName"]?.let { AppPackagesHelper.runPackage(it, context) }
                ok
            }
            "/runSystem" -> {
                params["packageName"]?.let { AppPackagesHelper.runSystemPackage(it, context) }
                ok
            }
            else -> RemoteServer.createPlainTextResponse(
                NanoHTTPD.Response.Status.NOT_FOUND, "Error 404, file not found."
            )
        }
    }
}
