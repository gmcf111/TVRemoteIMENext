package com.tvremoteime.next.server

import android.content.Context
import android.os.Build
import android.os.StatFs
import com.tvremoteime.next.core.AppPackagesHelper
import fi.iki.elonen.NanoHTTPD

/**
 * 杂项 GET 端点（对应旧版 OtherGetRequestProcesser.java）
 *
 * GET /version      → 当前版本号
 * GET /sdcard_stat  → 存储空间状态
 */
class OtherGetRequestProcessor(private val context: Context) : RequestProcessor {

    override fun isRequest(session: NanoHTTPD.IHTTPSession, fileName: String): Boolean {
        if (session.method != NanoHTTPD.Method.GET) return false
        return fileName == "/version" || fileName == "/sdcard_stat"
    }

    override fun doResponse(
        session: NanoHTTPD.IHTTPSession,
        fileName: String,
        params: Map<String, String>,
        files: Map<String, String>
    ): NanoHTTPD.Response = when (fileName) {
        "/version" -> RemoteServer.createPlainTextResponse(
            NanoHTTPD.Response.Status.OK, AppPackagesHelper.getCurrentPackageVersion(context)
        )
        "/sdcard_stat" -> getSDCardStatResponse()
        else -> RemoteServer.createPlainTextResponse(
            NanoHTTPD.Response.Status.NOT_FOUND, "Error 404, file not found."
        )
    }

    private fun getSDCardStatResponse(): NanoHTTPD.Response {
        val path = ServerFileManager.storageRoot
        val stat = StatFs(path.path)
        val totalBytes: Long
        val availableBytes: Long
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            totalBytes = stat.blockCount.toLong() * stat.blockSize
            availableBytes = stat.availableBlocks.toLong() * stat.blockSize
        } else {
            totalBytes = stat.totalBytes
            availableBytes = stat.availableBytes
        }
        return RemoteServer.createJSONResponse(
            NanoHTTPD.Response.Status.OK,
            "{\"totalBytes\":$totalBytes, \"availableBytes\":$availableBytes}"
        )
    }
}
