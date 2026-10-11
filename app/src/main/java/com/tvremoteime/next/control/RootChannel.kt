package com.tvremoteime.next.control

import android.content.Context
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executors

/**
 * Root 通道（02 文档 2.3，阶段 4）
 *
 * 能力：按键/文字（su input）、触控补位（input tap/swipe 全版本）、
 *      截屏回传（screencap，4.x CRLF 修复）、静默安装（pm install）、设置默认输入法（ime set）
 *
 * 探测：多 su 路径 + 结果缓存（用户开关 opt-in，见 RootPrefs）
 */
class RootChannel(private val context: Context) : ControlChannel {

    override val id: String = "root"
    override val capabilities: Set<Capability> = setOf(
        Capability.KEY, Capability.TEXT, Capability.TOUCH, Capability.SWIPE,
        Capability.SCROLL, Capability.SCREENSHOT, Capability.SILENT_INSTALL,
        Capability.SET_DEFAULT_IME
    )

    private val executor = Executors.newCachedThreadPool { r -> Thread(r, "root-channel") }

    /** su 探测缓存：null=未探测，true/false=结果 */
    @Volatile
    private var suAvailable: Boolean? = null

    override fun isAvailable(): Boolean {
        if (!RootPrefs.isEnabled(context)) return false
        suAvailable?.let { return it }
        val result = detectSu()
        suAvailable = result
        return result
    }

    /** 探测失败后强制重探（如用户刚刷入 root 或开启开关） */
    fun invalidateCache() {
        suAvailable = null
    }

    /** 多路径 su 探测（探测结果缓存于 suAvailable） */
    private fun detectSu(): Boolean {
        val suPaths = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/magisk/.core/bin/su")
        var found = false
        for (p in suPaths) {
            if (File(p).exists()) {
                found = true
                break
            }
        }
        if (!found) return false
        // 实际验证 su 可用（Magisk 拒绝授权时 which 有但命令失败）
        return runSuCommand("id", timeoutSec = 5)?.let { it.contains("uid=0") } == true
    }

    /** 兼容 API 19 的带超时等待（Process.waitFor(timeout) 需 API 26） */
    private fun waitWithTimeout(process: Process, timeoutSec: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutSec * 1000
        while (System.currentTimeMillis() < deadline) {
            try {
                process.exitValue()
                return true
            } catch (ignored: IllegalThreadStateException) {
            }
            try {
                Thread.sleep(50)
            } catch (ignored: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        process.destroy()  // destroyForcibly 需 API 26；destroy 在 19+ 可用，卡死进程由系统回收
        return false
    }

    /** 执行 su -c 命令，返回 stdout（合并 stderr 排查用），超时/失败返回 null */
    fun runSuCommand(command: String, timeoutSec: Long = 10): String? {
        return try {
            val process = ProcessBuilder("su", "-c", command).start()
            val out = ByteArrayOutputStream()
            val err = ByteArrayOutputStream()
            val tOut = Thread { process.inputStream.copyTo(out) }
            val tErr = Thread { process.errorStream.copyTo(err) }
            tOut.start(); tErr.start()
            if (!waitWithTimeout(process, timeoutSec)) {
                Log.e(TAG, "su command timeout: $command")
                return null
            }
            tOut.join(1000); tErr.join(1000)
            if (process.exitValue() == 0) out.toString("UTF-8") else {
                Log.e(TAG, "su command failed ($command): ${err.toString("UTF-8").trim()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "su command error: $command", e)
            null
        }
    }

    /** 执行 su -c 命令并取原始字节（screencap 用） */
    fun runSuBytes(command: String, timeoutSec: Long = 15): ByteArray? {
        return try {
            val process = ProcessBuilder("su", "-c", command).start()
            val out = ByteArrayOutputStream()
            val err = ByteArrayOutputStream()
            val tOut = Thread { process.inputStream.copyTo(out) }
            val tErr = Thread { process.errorStream.copyTo(err) }
            tOut.start(); tErr.start()
            if (!waitWithTimeout(process, timeoutSec)) {
                return null
            }
            tOut.join(1000); tErr.join(1000)
            if (process.exitValue() == 0) out.toByteArray() else null
        } catch (e: Exception) {
            Log.e(TAG, "su bytes error: $command", e)
            null
        }
    }

    // ============ 按键/文字 ============

    fun sendKey(keyCode: Int): Boolean =
        runSuCommand("input keyevent $keyCode", 5) != null || runShellKey(keyCode)

    fun sendText(text: String): Boolean {
        val escaped = text.replace(" ", "%s").replace("\"", "\\\"").replace("\\", "\\\\")
        return runSuCommand("input text \"$escaped\"", 8) != null
    }

    /** su 不可用但系统允许 input 的少数场景兜底 */
    private fun runShellKey(keyCode: Int): Boolean = try {
        Runtime.getRuntime().exec(arrayOf("sh", "-c", "input keyevent $keyCode"))
        true
    } catch (e: Exception) {
        false
    }

    // ============ 触控补位 ============

    /** 全版本 input tap（补上 4.4-6.x 无 Accessibility 手势的短板） */
    fun tap(x: Int, y: Int): Boolean =
        runSuCommand("input tap $x $y", 5) != null

    /** 全版本 input swipe */
    fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Int = 200): Boolean =
        runSuCommand("input swipe $startX $startY $endX $endY $durationMs", 5) != null

    // ============ 截屏（独占能力） ============

    /**
     * 截屏回传 PNG 字节。
     * 4.x ROM 的 `screencap -p` 经管道会把 \n 污染成 \r\n（R8 风险项），读取后修复。
     */
    fun screenshot(): ByteArray? {
        val data = runSuBytes("screencap -p", 20) ?: return null
        return fixCrlf(data)
    }

    /** 4.x CRLF 修复：PNG 内不应出现 \r\n，全部还原为 \n */
    private fun fixCrlf(data: ByteArray): ByteArray {
        // PNG 魔数快速判定
        if (data.size < 8 || data[0] != (-119).toByte() || data[1] != 0x50.toByte()) return data
        var dirty = false
        var i = 0
        while (i < data.size - 1) {
            if (data[i] == '\r'.code.toByte() && data[i + 1] == '\n'.code.toByte()) {
                dirty = true
                break
            }
            i++
        }
        if (!dirty) return data
        val out = ByteArrayOutputStream(data.size)
        var j = 0
        while (j < data.size) {
            val b = data[j]
            if (b == '\r'.code.toByte() && j + 1 < data.size && data[j + 1] == '\n'.code.toByte()) {
                out.write('\n'.code)
                j += 2
            } else {
                out.write(b.toInt())
                j++
            }
        }
        Log.i(TAG, "screenshot CRLF fixed: ${data.size} -> ${out.size()}")
        return out.toByteArray()
    }

    // ============ 静默安装/卸载（独占能力） ============

    /** root 静默安装；返回 null=成功，非 null=失败原因 */
    fun silentInstall(apkPath: String): String? {
        val result = runSuCommand(
            "pm install -r \"$apkPath\"", 60
        )
        return if (result != null) null else "pm install 执行失败（详见日志）"
    }

    fun silentUninstall(packageName: String): String? {
        val result = runSuCommand("pm uninstall $packageName", 30)
        return if (result != null) null else "pm uninstall 执行失败（详见日志）"
    }

    // ============ 系统域（独占能力） ============

    /** 设置本应用为系统默认输入法（替代旧版"请手动设置"的引导流程） */
    fun setDefaultIme(): Boolean {
        val imeId = "${context.packageName}/com.tvremoteime.next.IME.IMEService"
        return runSuCommand("ime set $imeId", 10) != null
    }

    /** root 可用时的快速探测结果文案（UI 展示） */
    fun describeStatus(): String {
        return when {
            !RootPrefs.isEnabled(context) -> "未开启"
            isAvailable() -> "可用（su 已授权）"
            else -> "已开启但 su 不可用"
        }
    }

    companion object {
        private const val TAG = "RootChannel"
    }
}

/** Root 开关偏好（用户 opt-in，02 文档） */
object RootPrefs {
    private const val PREFS = "root_settings"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("enabled", false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("enabled", enabled).commit()
    }
}
