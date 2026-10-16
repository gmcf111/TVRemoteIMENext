package com.tvremoteime.next.control

import android.content.Context
import android.view.KeyEvent
import com.tvremoteime.next.core.Env

/**
 * IME 通道（02 文档）
 *
 * 能力：KEY（经 InputConnection）/ TEXT（commitText，激活时优先）
 * 实现：InputConnection 操作委托给 IMEService 的静态句柄（HTTP 线程 → 主线程投递）
 */
class ImeChannel(private val context: Context) : ControlChannel {

    override val id: String = "ime"
    override val capabilities: Set<Capability> = setOf(Capability.KEY, Capability.TEXT)

    override fun isAvailable(): Boolean {
        // IME 服务被绑定即视为可用（HTTP 服务运行在本进程内，由它判断激活状态）
        return imeAvailable
    }

    /** 文字输入：直接经 IMEService 投递（激活时最快路径） */
    fun commitText(text: String): Boolean = imeTextHook?.invoke(text) == true

    /** 按键：经 IMEService 的 sendKeyCode 流程（含 HOME 拦截等旧版语义） */
    fun sendKey(keyCode: Int): Boolean = imeKeyHook?.invoke(keyCode) == true

    companion object {
        @Volatile
        var imeDispatchHook: ((String, Int) -> Boolean)? = null

        fun dispatchKey(code: String, action: Int): Boolean = imeDispatchHook?.invoke(code, action) == true

        /** IMEService onCreate/onDestroy 设置（服务存活标记） */
        @Volatile
        var imeAvailable: Boolean = false

        /** IMEService 注册的文字/按键回调（HTTP 线程调用，内部自行线程切换） */
        @Volatile
        var imeTextHook: ((String) -> Boolean)? = null

        @Volatile
        var imeKeyHook: ((Int) -> Boolean)? = null
    }
}
