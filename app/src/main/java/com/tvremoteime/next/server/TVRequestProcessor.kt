package com.tvremoteime.next.server

import android.content.Context
import android.util.Log
import com.tvremoteime.next.core.Env
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStreamWriter

/**
 * 电视直播源端点（对应旧版 TVRequestProcesser.java）
 *
 * GET  /tv.txt → 读取直播源
 * POST /tv.txt {text} → 保存直播源
 *
 * 与旧版差异（用户决策 D8）：不再内置默认源（旧版从 R.raw.tv 拷贝 2018 年失效源）。
 * 文件不存在时返回空内容。
 */
class TVRequestProcessor(private val context: Context) : RequestProcessor {

    private val tvFile: File get() = File(ServerFileManager.baseDir, "tv.txt")

    override fun isRequest(session: NanoHTTPD.IHTTPSession, fileName: String): Boolean =
        fileName.equals("/tv.txt", ignoreCase = true)

    override fun doResponse(
        session: NanoHTTPD.IHTTPSession,
        fileName: String,
        params: Map<String, String>,
        files: Map<String, String>
    ): NanoHTTPD.Response {
        return if (session.method == NanoHTTPD.Method.POST) {
            try {
                val text = params["text"] ?: ""
                OutputStreamWriter(FileOutputStream(tvFile), "utf-8").use { it.write(text) }
                RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.OK, "ok")
            } catch (e: Exception) {
                Log.e(Env.TAG, "POST /tv.txt", e)
                RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.OK, "fail")
            }
        } else {
            try {
                val inputStream = if (tvFile.exists()) FileInputStream(tvFile) else null
                if (inputStream == null) {
                    RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.OK, "")
                } else {
                    inputStream.use { input ->
                        NanoHTTPD.newFixedLengthResponse(
                            NanoHTTPD.Response.Status.OK,
                            "text/plain; charset=utf-8",
                            input, input.available().toLong()
                        )
                    }
                }
            } catch (e: Exception) {
                RemoteServer.createPlainTextResponse(
                    NanoHTTPD.Response.Status.INTERNAL_ERROR,
                    "SERVER INTERNAL ERROR: IOException: ${e.message}"
                )
            }
        }
    }
}
