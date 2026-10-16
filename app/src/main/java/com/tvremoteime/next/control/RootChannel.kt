package com.tvremoteime.next.control

import android.content.Context
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File

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

    /** su 探测缓存：null=未探测，true/false=结果 */
    @Volatile
    private var suAvailable: Boolean? = null
    @Volatile
    private var suExecutable: String = "su"

    override fun isAvailable(): Boolean {
        return RootPrefs.isEnabled(context) && suAvailable == true
    }

    /** 仅由用户主动开启 Root 开关触发，不在状态查询/进程重启时弹授权框。 */
    @Synchronized
    fun probe(): Boolean {
        if (!RootPrefs.isEnabled(context)) return false
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
        val suPaths = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/magisk/.core/bin/su", "su")
        for (p in suPaths) {
            if (p == "su" || File(p).canExecute()) {
                suExecutable = p
                if (runSuCommand("id", timeoutSec = 10)?.contains("uid=0") == true) return true
            }
        }
        return false
    }

    /** 兼容 API 19 的带超时等待（Process.waitFor(timeout) 需 API 26） */
    private fun waitWithTimeout(process: Process, timeoutSec: Long): Boolean {
        val deadline = System.nanoTime() + timeoutSec * 1_000_000_000L
        while (System.nanoTime() < deadline) {
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
    fun runSuCommand(command: String, timeoutSec: Long = 10): String? =
        runSuBytes(command, timeoutSec)?.toString(Charsets.UTF_8)

    /** 执行 su -c 命令并取原始字节（screencap 用） */
    fun runSuBytes(command: String, timeoutSec: Long = 15): ByteArray? {
        var process: Process? = null
        return try {
            val child = ProcessBuilder(suExecutable, "-c", command).start()
            process = child
            child.outputStream.close()
            val out = ByteArrayOutputStream()
            val err = ByteArrayOutputStream()
            val tOut = Thread { try { child.inputStream.use { it.copyTo(out) } } catch (_: java.io.IOException) { } }.apply { isDaemon = true }
            val tErr = Thread { try { child.errorStream.use { it.copyTo(err) } } catch (_: java.io.IOException) { } }.apply { isDaemon = true }
            tOut.start(); tErr.start()
            if (!waitWithTimeout(child, timeoutSec)) {
                return null
            }
            tOut.join(1000); tErr.join(1000)
            if (child.exitValue() == 0 && !tOut.isAlive && !tErr.isAlive) out.toByteArray() else null
        } catch (e: Exception) {
            Log.e(TAG, "su bytes error: $command", e)
            null
        } finally {
            process?.destroy()
            try { process?.inputStream?.close() } catch (_: java.io.IOException) { }
            try { process?.errorStream?.close() } catch (_: java.io.IOException) { }
        }
    }

    // ============ 按键/文字 ============

    fun sendKey(keyCode: Int): Boolean =
        runSuCommand("input keyevent $keyCode", 5) != null

    fun sendText(text: String): Boolean {
        return runSuCommand(ShellCommands.text(text), 8) != null
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
        return PngScreenshot.normalize(data)
    }

    // ============ 静默安装/卸载（独占能力） ============

    /** root 静默安装；返回 null=成功，非 null=失败原因 */
    fun silentInstall(apkPath: String): String? {
        val result = runSuCommand(
            "pm install -r ${ShellCommands.quote(apkPath)}", 60
        )
        return if (result?.trim() == "Success") null else "pm install 执行失败（详见日志）"
    }

    fun silentUninstall(packageName: String): String? {
        val result = runSuCommand("pm uninstall ${ShellCommands.quote(packageName)}", 30)
        return if (result?.trim() == "Success") null else "pm uninstall 执行失败（详见日志）"
    }

    // ============ 系统域（独占能力） ============

    /** 设置本应用为系统默认输入法（替代旧版"请手动设置"的引导流程） */
    fun setDefaultIme(): Boolean {
        val imeId = "${context.packageName}/com.tvremoteime.next.IME.IMEService"
        return runSuCommand("ime enable ${ShellCommands.quote(imeId)} && ime set ${ShellCommands.quote(imeId)}", 10) != null
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
