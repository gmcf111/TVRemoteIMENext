package com.tvremoteime.next.server

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import org.json.JSONArray
import org.json.JSONObject
import xllib.FileUtils

/**
 * 文件管理端点（对应旧版 FileRequestProcesser.java）
 *
 * GET  /file/dir/{path}     → 列目录
 * GET  /file/download/{path} → 下载
 * POST /file/copy|cut|delete|upload
 */
class FileRequestProcessor(private val context: Context) : RequestProcessor {

    override fun isRequest(session: NanoHTTPD.IHTTPSession, fileName: String): Boolean {
        return if (session.method == NanoHTTPD.Method.GET) {
            fileName.startsWith("/file/dir/") || fileName.startsWith("/file/download/")
        } else if (session.method == NanoHTTPD.Method.POST) {
            fileName == "/file/copy" || fileName == "/file/cut" ||
                    fileName == "/file/delete" || fileName == "/file/upload"
        } else false
    }

    override fun doResponse(
        session: NanoHTTPD.IHTTPSession,
        fileName: String,
        params: Map<String, String>,
        files: Map<String, String>
    ): NanoHTTPD.Response {
        if (session.method == NanoHTTPD.Method.GET) {
            return when {
                fileName.startsWith("/file/dir/") -> responseDirData(fileName.substring("/file/dir/".length))
                fileName.startsWith("/file/download/") -> downloadFileData(fileName.substring("/file/download/".length))
                else -> notFound()
            }
        }
        val paths = params["paths"]
        return when (fileName) {
            "/file/copy" -> {
                if (!paths.isNullOrEmpty()) batchCopyFile(params["targetPath"], paths)
                ok()
            }
            "/file/cut" -> {
                if (!paths.isNullOrEmpty()) batchCutFile(params["targetPath"], paths)
                ok()
            }
            "/file/delete" -> {
                if (!paths.isNullOrEmpty()) batchDeleteFile(paths)
                ok()
            }
            "/file/upload" -> uploadFile(params, files)
            else -> notFound()
        }
    }

    private fun ok() = RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.OK, "ok")
    private fun notFound() = RemoteServer.createPlainTextResponse(
        NanoHTTPD.Response.Status.NOT_FOUND, "Error 404, file not found."
    )

    private fun responseDirData(dirName: String): NanoHTTPD.Response {
        val root = ServerFileManager.storageRoot
        val path = File(root, dirName)
        val dirs = JSONArray()
        val files = JSONArray()
        return try {
            val subfiles = path.listFiles()?.sortedBy { it.name.lowercase() } ?: return notFound()
            for (file in subfiles) {
                if (file.isHidden) continue
                val item = JSONObject()
                item.put("name", file.name)
                item.put("path", file.path.substring(root.path.length))
                item.put("fullPath", file.path)
                if (file.isDirectory) {
                    dirs.put(item)
                } else {
                    item.put("size", file.length())
                    item.put("isMedia", FileUtils.isMediaFile(file.name))
                    files.put(item)
                }
            }
            val data = JSONObject()
            if (dirName.isNotEmpty() && dirName != "/") {
                data.put("parent", path.parent?.substring(root.path.length) ?: "")
            }
            data.put("dirs", dirs)
            data.put("files", files)
            RemoteServer.createJSONResponse(NanoHTTPD.Response.Status.OK, data.toString())
        } catch (e: Exception) {
            RemoteServer.createPlainTextResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "SERVER INTERNAL ERROR: JSONException: ${e.message}"
            )
        }
    }

    private fun downloadFileData(fileName: String): NanoHTTPD.Response {
        val file = File(ServerFileManager.storageRoot, fileName)
        if (!file.exists()) return notFound()
        return try {
            FileInputStream(file).use { input ->
                NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.OK,
                    "${NanoHTTPD.getMimeTypeForFile(file.name)}; charset=utf-8",
                    input, file.length()
                )
            }
        } catch (e: Exception) {
            notFound()
        }
    }

    private fun uploadFile(params: Map<String, String>, files: Map<String, String>): NanoHTTPD.Response {
        val uploadFileName = params["file"]
        val uploadPathName = params["path"]
        val localFilename = files["file"]
        var success = false
        if (!uploadFileName.isNullOrEmpty() && !localFilename.isNullOrEmpty()) {
            val saveDir = File(ServerFileManager.storageRoot, uploadPathName ?: "")
            val localFile = File(localFilename)
            val saveFile = File(saveDir, localFile.name)
            success = localFile.renameTo(saveFile)
        }
        return RemoteServer.createJSONResponse(
            NanoHTTPD.Response.Status.OK, "{\"success\":${if (success) "true" else "false"}}"
        )
    }

    private fun batchDeleteFile(paths: String) {
        for (p in paths.split("|")) {
            if (p.isNotEmpty()) ServerFileManager.deleteFile(File(ServerFileManager.storageRoot, p))
        }
    }

    private fun batchCopyFile(targetPath: String?, paths: String) {
        val targetPathFile = File(ServerFileManager.storageRoot, targetPath ?: "")
        if (!targetPathFile.exists()) return
        for (p in paths.split("|")) {
            if (p.isNotEmpty()) ServerFileManager.copyFile(File(ServerFileManager.storageRoot, p), targetPathFile)
        }
    }

    private fun batchCutFile(targetPath: String?, paths: String) {
        val targetPathFile = File(ServerFileManager.storageRoot, targetPath ?: "")
        if (!targetPathFile.exists()) return
        for (p in paths.split("|")) {
            if (p.isNotEmpty()) ServerFileManager.cutFile(File(ServerFileManager.storageRoot, p), targetPathFile)
        }
    }
}
