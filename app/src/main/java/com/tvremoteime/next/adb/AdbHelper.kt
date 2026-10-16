package com.tvremoteime.next.adb

import android.content.Context
import android.util.Log
import com.cgutman.adblib.AdbBase64
import com.cgutman.adblib.AdbConnection
import com.cgutman.adblib.AdbCrypto
import com.cgutman.adblib.Base64
import com.tvremoteime.next.control.ShellCommands
import com.tvremoteime.next.core.Env
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** 回环 ADB：单线程 FIFO 执行，持久化认证密钥，命令与普通输入文字类型分离。 */
class AdbHelper private constructor() {
    @Volatile private var connection: AdbConnection? = null
    @Volatile private var socket: Socket? = null
    @Volatile private var executor: ExecutorService? = null
    @Volatile private var connected = false
    private var context: Context? = null
    private var host = "127.0.0.1"
    private var port = 5555

    val isRunning: Boolean get() = executor?.isShutdown == false
    val isConnected: Boolean get() = connected

    @Synchronized
    fun init(context: Context, host: String, port: Int) {
        if (isRunning) return
        this.context = context.applicationContext
        this.host = host
        this.port = port
        executor = Executors.newSingleThreadExecutor { Thread(it, "adb-commands") }.also {
            it.execute { connect() }
        }
    }

    @Synchronized
    fun stop() {
        executor?.shutdownNow()
        executor = null
        disconnect()
    }

    private fun disconnect() {
        connected = false
        val old = connection
        connection = null
        try { socket?.close() } catch (_: IOException) { }
        try { old?.close() } catch (_: IOException) { }
    }

    private fun connect(): Boolean {
        if (connected) return true
        val app = context ?: return false
        disconnect()
        return try {
            val transport = Socket()
            socket = transport
            transport.connect(InetSocketAddress(host, port), 3000)
            transport.soTimeout = 10000
            val base64 = AdbBase64 { Base64.encodeToString(it, 16) }
            val privateKey = File(app.filesDir, "adbkey")
            val publicKey = File(app.filesDir, "adbkey.pub")
            val crypto = try {
                AdbCrypto.loadAdbKeyPair(base64, privateKey, publicKey)
            } catch (_: Exception) {
                AdbCrypto.generateAdbKeyPair(base64).also { it.saveAdbKeyPair(privateKey, publicKey) }
            }
            val candidate = AdbConnection.create(transport, crypto)
            connection = candidate
            candidate.setOnClosedListener {
                if (connection === candidate) connected = false
                try { candidate.close() } catch (_: IOException) { }
            }
            candidate.connect()
            connected = true
            true
        } catch (error: Exception) {
            disconnect()
            Log.w(TAG, "ADB connection unavailable", error)
            false
        }
    }

    fun sendData(data: Any): Boolean = when (data) {
        is Int -> sendShell("input keyevent $data")
        is String -> sendShell(ShellCommands.text(data))
        else -> false
    }

    fun sendShell(command: String): Boolean {
        val worker = executor ?: return false
        if (worker.isShutdown) return false
        val task = try {
            worker.submit<Boolean> {
                if (!connect()) return@submit false
                val output = ByteArrayOutputStream()
                try {
                    connection!!.open("shell:$command; echo __TVREMOTE_STATUS__:\$?").use { stream ->
                        try {
                            while (true) output.write(stream.read())
                        } catch (_: IOException) { }
                    }
                    output.toString("UTF-8").trimEnd().endsWith("__TVREMOTE_STATUS__:0")
                } catch (error: Exception) {
                    disconnect()
                    false
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) { return false }
        return try {
            task.get(15, TimeUnit.SECONDS)
        } catch (_: Exception) {
            task.cancel(true)
            false
        }
    }

    companion object {
        private const val TAG = "AdbHelper"
        @Volatile private var instance: AdbHelper? = null
        fun getInstance(): AdbHelper? = instance
        @Synchronized fun createInstance() { if (instance == null) instance = AdbHelper() }
        fun initService(context: Context): Boolean {
            val helper = instance ?: return false
            helper.init(context, "127.0.0.1", Env.adbServerPort)
            return true
        }
        fun stopService() { instance?.stop() }
    }
}
