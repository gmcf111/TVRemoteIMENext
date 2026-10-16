package com.tvremoteime.next.server

import android.content.Context
import com.tvremoteime.next.core.FileAccess
import com.tvremoteime.next.core.MediaTypes
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject

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

    private fun safeFile(requestedPath: String?): File? =
        FileAccess.resolve(ServerFileManager.storageRoot, requestedPath)

    override fun doResponse(
        session: NanoHTTPD.IHTTPSession,
        fileName: String,
        params: Map<String, String>,
        files: Map<String, String>
    ): NanoHTTPD.Response {
        ServerFileManager.refreshStorageRoot(context)
        if (session.method == NanoHTTPD.Method.GET) {
            return when {
                fileName.startsWith("/file/dir/") -> responseDirData(fileName.substring("/file/dir/".length))
                fileName.startsWith("/file/download/") -> downloadFileData(fileName.substring("/file/download/".length))
                else -> notFound()
            }
        }
        if (fileName == "/file/upload") return uploadFile(params, files)
        return try {
            // 新客户端用 JSON 数组支持文件名中的 |，保留旧 paths 参数。
            val paths = params["pathsJson"]?.let { json ->
                val array = JSONArray(json)
                List(array.length()) { array.getString(it) }
            } ?: params["paths"]?.split('|')?.filter { it.isNotEmpty() }.orEmpty()
            require(paths.isNotEmpty()) { "No files selected" }
            val sources = paths.map { safeFile(it) ?: throw IOException("Invalid path") }
            require(sources.none { it == ServerFileManager.storageRoot.canonicalFile }) { "Cannot modify storage root" }
            when (fileName) {
                "/file/copy", "/file/cut" -> {
                    val target = safeFile(params["targetPath"]) ?: throw IOException("Invalid destination")
                    sources.forEach { FileAccess.copy(it, target, fileName == "/file/cut") }
                }
                "/file/delete" -> sources.forEach {
                    if (!it.exists() || !it.deleteRecursively()) throw IOException("Cannot delete ${it.name}")
                }
                else -> return notFound()
            }
            ok()
        } catch (error: Exception) {
            RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.BAD_REQUEST, error.message ?: "File operation failed")
        }
    }

    private fun ok() = RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.OK, "ok")
    private fun notFound() = RemoteServer.createPlainTextResponse(
        NanoHTTPD.Response.Status.NOT_FOUND, "Error 404, file not found."
    )

    private fun responseDirData(dirName: String): NanoHTTPD.Response {
        val root = ServerFileManager.storageRoot.canonicalFile
        val path = safeFile(dirName) ?: return notFound()
        val dirs = JSONArray()
        val files = JSONArray()
        return try {
            val subfiles = path.listFiles()?.sortedBy { it.name.lowercase() } ?: return notFound()
            for (file in subfiles) {
                if (file.isHidden) continue
                val item = JSONObject()
                item.put("name", file.name)
                item.put("path", "/" + file.relativeTo(root).invariantSeparatorsPath)
                item.put("fullPath", file.path)
                if (file.isDirectory) {
                    dirs.put(item)
                } else {
                    item.put("size", file.length())
                    item.put("isMedia", MediaTypes.isMediaFile(file.name))
                    files.put(item)
                }
            }
            val data = JSONObject()
            if (path != root) {
                data.put("parent", path.parentFile?.relativeTo(root)?.invariantSeparatorsPath ?: "")
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
        if (!file.isFile) return notFound()
        return try {
            NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK, NanoHTTPD.getMimeTypeForFile(file.name),
                FileInputStream(file), file.length()
            )
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
            try {
                FileAccess.copy(localFile, saveDir, move = true)
                success = true
            } catch (_: Exception) { }
        }
        return RemoteServer.createJSONResponse(
            NanoHTTPD.Response.Status.OK, "{\"success\":${if (success) "true" else "false"}}"
        )
    }

}
