package com.tvremoteime.next.server

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject
import xllib.FileUtils

/**
 * 文件管理端点（对应旧版 FileRequestProcesser.java）
 *
 * GET  /file/dir/{path}     → 列目录
 * GET  /file/download/{path} → 下载
 * POST /file/copy|cut|delete|upload
 *
 * 阶段 3 起对客户端路径做净化（吸收 cacaview SecurityManager 的防路径穿越逻辑）
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

    /** 防路径穿越：解码 URL、去空字节、正反斜杠统一、拒绝 .. 及回溯模式、去前导斜杠 */
    private fun sanitizePath(requestedPath: String?): String? {
        if (requestedPath == null) return null
        var path = try {
            java.net.URLDecoder.decode(requestedPath, "UTF-8")
        } catch (e: Exception) {
            return null
        }
        if (path.contains("\u0000")) return null
        path = path.replace('\\', '/')
        if (path.contains("../") || path.contains("/..") || path == "..") return null
        while (path.startsWith("/")) path = path.substring(1)
        return path
    }

    /** 将净化后的相对路径解析到 storageRoot 内的 File，越界返回 null */
    private fun safeFile(requestedPath: String?): File? {
        val sanitized = sanitizePath(requestedPath) ?: return null
        val root = ServerFileManager.storageRoot
        val file = File(root, sanitized)
        return try {
            if (file.canonicalPath.startsWith(root.canonicalPath)) file else null
        } catch (e: IOException) {
            null
        }
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
        val path = safeFile(dirName) ?: return notFound()
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
        val file = safeFile(fileName) ?: return notFound()
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
            val saveDir = safeFile(uploadPathName) ?: return RemoteServer.createJSONResponse(NanoHTTPD.Response.Status.OK, "{\"success\":false}")
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
            if (p.isNotEmpty()) safeFile(p)?.let { ServerFileManager.deleteFile(it) }
        }
    }

    private fun batchCopyFile(targetPath: String?, paths: String) {
        val targetPathFile = safeFile(targetPath) ?: return
        if (!targetPathFile.exists()) return
        for (p in paths.split("|")) {
            if (p.isNotEmpty()) safeFile(p)?.let { ServerFileManager.copyFile(it, targetPathFile) }
        }
    }

    private fun batchCutFile(targetPath: String?, paths: String) {
        val targetPathFile = safeFile(targetPath) ?: return
        if (!targetPathFile.exists()) return
        for (p in paths.split("|")) {
            if (p.isNotEmpty()) safeFile(p)?.let { ServerFileManager.cutFile(it, targetPathFile) }
        }
    }
}
