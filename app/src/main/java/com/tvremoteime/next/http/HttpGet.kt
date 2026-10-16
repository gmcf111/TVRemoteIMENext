package com.tvremoteime.next.http

import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** 使用平台默认 TLS 验证；连接和文件流均在完成后关闭。 */
object HttpGet {
    fun readString(uri: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = open(uri)
            if (connection.responseCode == 200) connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() } else null
        } catch (error: Exception) {
            Log.e("HttpGet", "readString", error)
            null
        } finally {
            connection?.disconnect()
        }
    }

    fun downloadFile(uri: String, file: File): Boolean {
        var connection: HttpURLConnection? = null
        var temp: File? = null
        return try {
            connection = open(uri)
            if (connection.responseCode != 200) return false
            file.parentFile?.mkdirs()
            temp = File.createTempFile("download-", ".part", file.parentFile)
            val length = connection.getHeaderField("Content-Length")?.toLongOrNull()
            val copied = connection.inputStream.use { input -> temp.outputStream().use { input.copyTo(it) } }
            if (length != null && copied != length) return false
            if (temp.renameTo(file)) true else {
                temp.copyTo(file, overwrite = true)
                true
            }
        } catch (error: Exception) {
            Log.e("HttpGet", "downloadFile", error)
            false
        } finally {
            temp?.delete()
            connection?.disconnect()
        }
    }

    private fun open(uri: String): HttpURLConnection = (URL(uri).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        setRequestProperty("Accept-Encoding", "identity")
        setRequestProperty("User-Agent", "TVRemoteIMENext")
        connectTimeout = 15000
        readTimeout = 30000
    }
}
