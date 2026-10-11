package com.tvremoteime.next.server

import android.content.Context
import com.tvremoteime.next.core.AppPackagesHelper
import com.tvremoteime.next.core.VideoPlayHelper
import fi.iki.elonen.NanoHTTPD
import xllib.FileUtils
import java.io.File

/**
 * 文件上传端点（对应旧版 UploadRequestProcesser.java）
 *
 * POST /upload {autoInstall, useSystem} file → 传文件；APK 可自动安装、媒体自动播放
 */
class UploadRequestProcessor(private val context: Context) : RequestProcessor {

    override fun isRequest(session: NanoHTTPD.IHTTPSession, fileName: String): Boolean =
        session.method == NanoHTTPD.Method.POST && fileName.equals("/upload", ignoreCase = true)

    override fun doResponse(
        session: NanoHTTPD.IHTTPSession,
        fileName: String,
        params: Map<String, String>,
        files: Map<String, String>
    ): NanoHTTPD.Response {
        val uploadFileName = params["file"]
        val autoInstall = params["autoInstall"] == "true"
        val localFilename = files["file"]
        if (!uploadFileName.isNullOrEmpty() && !localFilename.isNullOrEmpty() && autoInstall) {
            if (localFilename.endsWith(".apk")) {
                AppPackagesHelper.installPackage(File(localFilename), context)
            } else if (FileUtils.isMediaFile(localFilename)) {
                VideoPlayHelper.playUrl(context, localFilename, 0, params["useSystem"] == "true")
            }
        }
        return if (localFilename.isNullOrEmpty()) {
            RemoteServer.createJSONResponse(NanoHTTPD.Response.Status.OK, "{\"success\":false}")
        } else {
            val escapedPath = localFilename.replace("\\", "\\\\")
            RemoteServer.createJSONResponse(
                NanoHTTPD.Response.Status.OK,
                "{\"success\":true, \"filePath\":\"$escapedPath\"}"
            )
        }
    }
}
