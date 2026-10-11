package com.tvremoteime.next.server

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.tvremoteime.next.R
import com.tvremoteime.next.core.Env
import fi.iki.elonen.NanoHTTPD
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException

/**
 * HTTP 控制服务（对应旧版 RemoteServer.java）
 *
 * 端点路径 100% 兼容旧版；触控板端点（/mouse 系列）阶段 3 接入，
 * /channel-info、/screenshot、/theme 阶段 3-5 接入（02 文档）。
 */
class RemoteServer(
    port: Int,
    private val context: Context
) : NanoHTTPD(port) {

    interface DataReceiver {
        /** keyAction: 0 = keypressed, 1 = keydown, 2 = keyup */
        fun onKeyEventReceived(keyCode: String, keyAction: Int)
        fun onTextReceived(text: String)
    }

    var dataReceiver: DataReceiver? = null
    private var isStarted = false

    private val getRequestProcessors = mutableListOf<RequestProcessor>()
    private val postRequestProcessors = mutableListOf<RequestProcessor>()

    init {
        addGetRequestProcessors()
        addPostRequestProcessors()
    }

    val starting: Boolean get() = isStarted

    @Throws(IOException::class)
    override fun start(timeout: Int, daemon: Boolean) {
        isStarted = true
        tempFileManagerFactory = ServerFileManager.Factory()
        super.start(timeout, daemon)
    }

    override fun stop() {
        super.stop()
        isStarted = false
    }

    private fun addGetRequestProcessors() {
        getRequestProcessors.apply {
            add(RawRequestProcessor(context, "/index.html", R.raw.index, NanoHTTPD.MIME_HTML))
            add(RawRequestProcessor(context, "/style.css", R.raw.style, "text/css"))
            add(RawRequestProcessor(context, "/jquery_min.js", R.raw.jquery_min, "application/x-javascript"))
            add(RawRequestProcessor(context, "/ime_core.js", R.raw.ime_core, "application/x-javascript"))
            add(RawRequestProcessor(context, "/keys.png", R.raw.keys, "image/png"))
            add(RawRequestProcessor(context, "/ic_dl_folder.png", R.raw.ic_dl_folder, "image/png"))
            add(RawRequestProcessor(context, "/ic_dl_other.png", R.raw.ic_dl_other, "image/png"))
            add(RawRequestProcessor(context, "/ic_dl_video.png", R.raw.ic_dl_video, "image/png"))
            add(RawRequestProcessor(context, "/favicon.ico", R.drawable.ic_launcher, "image/x-icon"))
            add(FileRequestProcessor(context))
            add(AppIconRequestProcessor(context))
            add(TVRequestProcessor(context))
            add(MouseRequestProcessor(context, this@RemoteServer))
            add(ChannelInfoRequestProcessor(context))
            add(ScreenshotRequestProcessor(context))
            add(ThemeRequestProcessor(context))
            add(OtherGetRequestProcessor(context))
        }
    }

    private fun addPostRequestProcessors() {
        postRequestProcessors.apply {
            add(InputRequestProcessor(context, this@RemoteServer))
            add(MouseRequestProcessor(context, this@RemoteServer))
            add(UploadRequestProcessor(context))
            add(AppRequestProcessor(context))
            add(PlayRequestProcessor(context))
            add(FileRequestProcessor(context))
            add(TVRequestProcessor(context))
            add(TorrentRequestProcessor(context))
            add(ThemeRequestProcessor(context))
            add(OtherPostRequestProcessor(context))
        }
    }

    override fun serve(session: IHTTPSession): Response {
        Log.i(Env.TAG, "接收到HTTP请求：${session.method} ${session.uri}")
        if (session.uri.isNotEmpty()) {
            var fileName = session.uri.trim()
            val qIdx = fileName.indexOf('?')
            if (qIdx >= 0) fileName = fileName.substring(0, qIdx)

            if (session.method == Method.GET) {
                for (processor in getRequestProcessors) {
                    if (processor.isRequest(session, fileName)) {
                        return processor.doResponse(session, fileName, session.parms, emptyMap())
                    }
                }
            } else if (session.method == Method.POST) {
                val files = HashMap<String, String>()
                try {
                    session.parseBody(files)
                } catch (ioex: IOException) {
                    return createPlainTextResponse(
                        NanoHTTPD.Response.Status.INTERNAL_ERROR, "SERVER INTERNAL ERROR: IOException: ${ioex.message}"
                    )
                } catch (rex: ResponseException) {
                    return createPlainTextResponse(rex.status, rex.message ?: "")
                }
                for (processor in postRequestProcessors) {
                    if (processor.isRequest(session, fileName)) {
                        return processor.doResponse(session, fileName, session.parms, files)
                    }
                }
            }
        }
        // 默认页：index.html
        return getRequestProcessors[0].doResponse(session, "", emptyMap(), emptyMap())
    }

    fun getServerAddress(): String = getServerAddress(context)

    companion object {
        var serverPort = 9978

        fun getLocalIPAddress(context: Context): String {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ipAddress = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (ipAddress == 0) {
                try {
                    val enumerationNi = NetworkInterface.getNetworkInterfaces()
                    while (enumerationNi.hasMoreElements()) {
                        val networkInterface = enumerationNi.nextElement()
                        val interfaceName = networkInterface.displayName
                        if (interfaceName == "eth0" || interfaceName == "wlan0") {
                            val enumIpAddr = networkInterface.inetAddresses
                            while (enumIpAddr.hasMoreElements()) {
                                val inetAddress = enumIpAddr.nextElement()
                                if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                                    return inetAddress.hostAddress ?: "0.0.0.0"
                                }
                            }
                        }
                    }
                } catch (e: SocketException) {
                    Log.e(Env.TAG, "获取本地IP出错", e)
                }
            } else {
                return formatIp(ipAddress)
            }
            return "0.0.0.0"
        }

        private fun formatIp(ipAddress: Int): String =
            "${ipAddress and 0xff}.${ipAddress shr 8 and 0xff}.${ipAddress shr 16 and 0xff}.${ipAddress shr 24 and 0xff}"

        fun getServerAddress(context: Context): String {
            val ipAddress = getLocalIPAddress(context)
            Env.debug("ip-address:$ipAddress")
            return "http://$ipAddress:$serverPort/"
        }

        fun createPlainTextResponse(status: NanoHTTPD.Response.IStatus, text: String): NanoHTTPD.Response =
            NanoHTTPD.newFixedLengthResponse(status, NanoHTTPD.MIME_PLAINTEXT, text)

        fun createJSONResponse(status: NanoHTTPD.Response.IStatus, text: String): NanoHTTPD.Response =
            NanoHTTPD.newFixedLengthResponse(status, "application/json", text)
    }
}
