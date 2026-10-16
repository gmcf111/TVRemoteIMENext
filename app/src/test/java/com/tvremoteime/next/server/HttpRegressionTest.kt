package com.tvremoteime.next.server

import android.app.Application
import android.content.Context
import com.tvremoteime.next.theme.ThemePrefs
import fi.iki.elonen.NanoHTTPD
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class HttpRegressionTest {
    private lateinit var server: RemoteServer
    private lateinit var context: Context

    @Before fun start() {
        context = RuntimeEnvironment.getApplication()
        ServerFileManager.resetBaseDir(context)
        server = RemoteServer(0, context)
        server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true)
    }

    @After fun stop() { server.stop() }

    private fun request(path: String, body: ByteArray? = null, type: String = "application/x-www-form-urlencoded"): Pair<Int, ByteArray> {
        val connection = URL("http://127.0.0.1:${server.listeningPort}$path").openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        try {
            if (body != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", type)
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            return code to (if (code < 400) connection.inputStream else connection.errorStream).use { it.readBytes() }
        } finally { connection.disconnect() }
    }

    @Test fun staticResourcesAreReadableAfterProcessorReturns() {
        val html = request("/")
        assertEquals(200, html.first)
        assertTrue(html.second.toString(Charsets.UTF_8).contains("touchpad"))
        assertTrue(request("/ime_core.js").second.size > 10000)
        assertTrue(request("/style.css").second.size > 1000)
    }

    @Test fun playUrlRejectsThunderLinksAfterFeatureRemoval() {
        // v3.0：迅雷/种子地址不再支持，/play 应静默拒绝（返回 ok 但不崩溃）
        val response = request("/play", "playUrl=thunder%3A%2F%2Ftest&useSystem=false".toByteArray())
        assertEquals(200, response.first)
        assertEquals("ok", response.second.toString(Charsets.UTF_8))
    }

    @Test fun downloadRetainsStreamAndDecodesFilenameExactlyOnce() {
        val name = "中文+100%25.txt"
        val root = com.tvremoteime.next.core.StorageHelper.getStorageRoot(context).apply { mkdirs() }
        val bytes = ByteArray(128 * 1024) { (it % 251).toByte() }
        File(root, name).writeBytes(bytes)
        val response = request("/file/download/" + URLEncoder.encode(name, "UTF-8").replace("+", "%20"))
        assertEquals(200, response.first)
        assertArrayEquals(bytes, response.second)
    }

    @Test fun emptyRootUploadKeepsFilenameAndBytes() {
        val name = "test+100%.txt"
        val body = ("--test-boundary\r\nContent-Disposition: form-data; name=\"path\"\r\n\r\n\r\n" +
            "--test-boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"test%2B100%25.txt\"\r\n" +
            "Content-Type: text/plain\r\n\r\npayload\r\n--test-boundary--\r\n").toByteArray()
        val response = request("/file/upload", body, "multipart/form-data; boundary=test-boundary")
        assertTrue(response.second.toString(Charsets.UTF_8).contains("\"success\":true"))
        assertEquals("payload", File(ServerFileManager.storageRoot, name).readText())
    }

    @Test fun invalidFileOperationIsNotReportedAsSuccess() {
        assertEquals(400, request("/file/copy", "targetPath=&paths=../outside".toByteArray()).first)
    }

    @Test fun unavailableInputIsNotReportedAsSuccess() {
        assertEquals(503, request("/text", "text=hello".toByteArray()).first)
    }

    @Test fun shorthandHexIsAcceptedAndExpanded() {
        assertEquals(0xFFAABBCC.toInt(), ThemePrefs.parseHex("#ABC"))
        assertNull(ThemePrefs.parseHex("#GGG"))
    }
}
