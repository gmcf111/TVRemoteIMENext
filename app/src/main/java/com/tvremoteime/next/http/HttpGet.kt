package com.tvremoteime.next.http

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * HTTP 下载工具（对应旧版 HTTPGet.java）
 *
 * 旧版强转 HttpsURLConnection 在 http:// URL 上会 ClassCastException；
 * 新版按协议分派。自更新源为 GitHub Releases（https），仍保留信任所有证书逻辑。
 */
object HttpGet {
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

    fun readString(uri: String): String? = try {
        val conn = open(uri) ?: return null
        if (conn.responseCode == 200) {
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } else null
    } catch (e: Exception) {
        Log.e("HttpGet", "readString", e); null
    }

    fun downloadFile(uri: String, file: File): Boolean = try {
        val conn = open(uri) ?: return false
        if (conn.responseCode == 200) {
            conn.inputStream.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output, 8192)
                }
            }
            true
        } else false
    } catch (e: Exception) {
        Log.e("HttpGet", "downloadFile", e); false
    }

    private fun open(uri: String): HttpURLConnection? {
        TrustAll.allowAllSSL()
        val conn = URL(uri).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "text/html, application/xhtml+xml, image/jxr, */*")
        conn.setRequestProperty("Accept-Encoding", "identity")
        conn.setRequestProperty("User-Agent", UA)
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        return conn
    }
}

/** 对应旧版 HTTPSTrustManager.java（信任所有证书；仅用于自更新下载场景） */
object TrustAll {
    private var trustManagers: Array<javax.net.ssl.TrustManager>? = null

    fun allowAllSSL() {
        HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
        if (trustManagers == null) {
            trustManagers = arrayOf(object : javax.net.ssl.X509TrustManager {
                override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
            })
        }
        try {
            val context = javax.net.ssl.SSLContext.getInstance("TLS")
            context.init(null, trustManagers, java.security.SecureRandom())
            HttpsURLConnection.setDefaultSSLSocketFactory(context.socketFactory)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
