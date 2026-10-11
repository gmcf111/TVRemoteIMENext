package com.tvremoteime.next.adb

import android.content.Context
import android.util.Log
import com.cgutman.adblib.AdbBase64
import com.cgutman.adblib.AdbConnection
import com.cgutman.adblib.AdbCrypto
import com.cgutman.adblib.Base64
import com.tvremoteime.next.core.Env
import com.tvremoteime.next.server.RemoteServer
import java.io.IOException
import java.net.Socket
import java.security.NoSuchAlgorithmException
import java.util.ArrayDeque

/**
 * ADB 通道（对应旧版 AdbHelper.java）
 *
 * 通过本机回环 adbd（127.0.0.1:5555）发送 input keyevent/text。
 * 四通道架构（02 文档）中阶段 3 将重构为 AdbChannel，
 * 本阶段保持与旧版等价的行为以先恢复功能。
 */
class AdbHelper private constructor() {
    private var connection: AdbConnection? = null
    private var host: String? = null
    private var port = 0

    private val sendDataDeque = ArrayDeque<Any>()
    private val dequeMonitor = Object()
    private var sendDataThread: Thread? = null

    @Volatile
    private var running = false
    private var context: Context? = null

    val isRunning: Boolean get() = running

    fun init(context: Context, host: String, port: Int) {
        this.context = context
        this.host = host
        this.port = port
        this.running = true
        initSendThread()
    }

    fun stop() {
        running = false
        context = null
        try {
            connection?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        connection = null
        synchronized(dequeMonitor) { dequeMonitor.notifyAll() }
        sendDataThread?.let {
            if (it.isAlive) {
                it.interrupt()
                try {
                    it.join()
                } catch (ignored: InterruptedException) {
                }
            }
        }
    }

    private fun initSendThread() {
        if (sendDataThread == null || sendDataThread?.isAlive != true) {
            sendDataThread = Thread {
                var data: Any
                while (running) {
                    synchronized(dequeMonitor) {
                        try {
                            if (sendDataDeque.isEmpty()) dequeMonitor.wait()
                        } catch (e: InterruptedException) {
                        }
                        data = if (sendDataDeque.isNotEmpty()) {
                            sendDataDeque.pop()
                        } else continue
                    }
                    val msg = if (data is Int) {
                        "shell:input keyevent ${(data as Int).toString()}"
                    } else {
                        "shell:input text \"" + (data as String)
                            .replace("\"", "\\\"")
                            .replace("\\", "\\\\") + "\""
                    }
                    try {
                        if (connection != null || try2Connect()) {
                            connection?.open(msg)
                            Env.debug("已成功发送adb命令：$msg")
                        } else {
                            Env.debug("未发送adb命令：$msg")
                        }
                    } catch (e: Exception) {
                        Env.debug("发送adb命令时出错：$msg", e)
                    }
                }
            }.also { it.start() }
        }
    }

    private fun try2Connect(): Boolean {
        return try {
            val socket = Socket(host, port)
            socket.soTimeout = 1000 * 10
            var crypto: AdbCrypto? = null
            try {
                crypto = AdbCrypto.generateAdbKeyPair(AdbBase64 { data -> Base64.encodeToString(data, 16) })
            } catch (e: NoSuchAlgorithmException) {
                e.printStackTrace()
            }
            val appContext = context
            if (connection == null) {
                connection = AdbConnection.create(socket, crypto)
                connection?.setOnClosedListener {
                    Log.i(TAG, "adb已断开连接。")
                    appContext?.let {
                        Env.toastInHandler(it, it.getString(com.tvremoteime.next.R.string.app_name) + "和adb服务已断开连接。")
                    }
                    try {
                        connection?.close()
                        connection = null
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
            connection?.connect()
            Log.i(TAG, "adb已连接成功。")
            appContext?.let {
                Env.toastInHandler(it, it.getString(com.tvremoteime.next.R.string.app_name) + "和adb服务已连接成功。")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "adb连接失败，错误信息。${e}", e)
            connection = null
            false
        }
    }

    fun sendData(data: Any) {
        sendDataDeque.push(data)
        synchronized(dequeMonitor) { dequeMonitor.notifyAll() }
    }

    companion object {
        private const val TAG = "AdbHelper"

        @Volatile
        private var instance: AdbHelper? = null

        fun getInstance(): AdbHelper? = instance

        fun createInstance() {
            if (instance == null) {
                synchronized(AdbHelper::class.java) {
                    if (instance == null) instance = AdbHelper()
                }
            }
        }

        fun initService(context: Context): Boolean {
            val inst = instance ?: return false
            if (!inst.isRunning) {
                inst.init(context, RemoteServer.getLocalIPAddress(context), Env.adbServerPort)
            }
            return true
        }

        fun stopService() {
            instance?.stop()
        }
    }
}
