package com.tvremoteime.next.control

import android.content.Context
import org.json.JSONObject

/**
 * 控制中心（02 文档）
 *
 * 进程内单例：组装四通道 + 路由器，供 HTTP 处理器 / IMEService / MainActivity 使用。
 */
object ControlCenter {

    @Volatile
    private var routerInternal: ChannelRouter? = null

    @Volatile
    private var rootChannelInternal: RootChannel? = null
    private var mouseInternal: MouseController? = null

    fun init(context: Context) {
        if (routerInternal == null) {
            synchronized(this) {
                if (routerInternal == null) {
                    val appContext = context.applicationContext
                    rootChannelInternal = RootChannel(appContext)
                    mouseInternal = MouseController(appContext)
                    routerInternal = ChannelRouter(
                        listOf(
                            rootChannelInternal!!,
                            ImeChannel(appContext),
                            AccessibilityChannel(),
                            AdbChannel()
                        )
                    )
                }
            }
        }
    }

    fun router(): ChannelRouter = routerInternal ?: ChannelRouter(emptyList())

    fun root(): RootChannel? = rootChannelInternal
    fun mouse(): MouseController = checkNotNull(mouseInternal) { "ControlCenter.init must be called first" }

    fun sendKey(code: Int): Boolean = router().execute(Capability.KEY) {
        when (it) {
            is RootChannel -> it.sendKey(code)
            is ImeChannel -> it.sendKey(code)
            is AdbChannel -> it.sendKey(code)
            else -> false
        }
    }

    fun sendText(text: String): Boolean = router().execute(Capability.TEXT) {
        when (it) {
            is ImeChannel -> it.commitText(text)
            is RootChannel -> it.sendText(text)
            is AdbChannel -> it.sendText(text)
            else -> false
        }
    }

    // ============ /channel-info 上报（02 文档 2.4） ============

    fun channelInfoJson(context: Context): String {
        val r = router()
        val root = r.channel("root") as? RootChannel
        val ime = r.channel("ime") as? ImeChannel
        val a11y = r.channel("a11y") as? AccessibilityChannel
        val adb = r.channel("adb") as? AdbChannel

        val rootAvailable = root?.isAvailable() == true
        val imeAvailable = ime?.isAvailable() == true
        val a11yAvailable = a11y?.isAvailable() == true
        val adbAvailable = adb?.isAvailable() == true

        val rootCaps = root?.capabilities?.map { it.name } ?: emptyList()
        val active = r.activeChannels()

        return JSONObject().apply {
            put("root", JSONObject().apply {
                put("available", rootAvailable)
                put("enabled", com.tvremoteime.next.control.RootPrefs.isEnabled(context))
                put("capabilities", org.json.JSONArray(rootCaps))
            })
            put("ime", JSONObject().apply {
                put("available", imeAvailable)
                put("isDefault", com.tvremoteime.next.core.Env.isDefaultIME(context))
            })
            put("accessibility", JSONObject().apply {
                put("available", a11yAvailable)
                put("needsApi", 24)
            })
            put("adb", JSONObject().apply {
                put("available", adbAvailable)
            })
            put("activeChannels", JSONObject().apply {
                active.forEach { (k, v) -> put(k, v ?: JSONObject.NULL) }
            })
        }.toString()
    }
}
