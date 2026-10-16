package com.tvremoteime.next.control

import android.content.Context
import android.view.KeyEvent
import com.tvremoteime.next.adb.AdbHelper
import com.tvremoteime.next.core.Env

/**
 * ADB 通道（02 文档）
 *
 * 能力：KEY / TEXT（回环 adbd shell:input）；可用条件：盒子开启 ADB（5555）且连接成功
 * 说明：本阶段（阶段 4）保持旧版 AdbHelper 行为，作为按键/文字域的兜底通道；
 *      tap/swipe 等触控命令由 MouseAccessibilityService 回退时直接调用 AdbHelper。
 */
class AdbChannel : ControlChannel {

    override val id: String = "adb"
    override val capabilities: Set<Capability> = setOf(Capability.KEY, Capability.TEXT, Capability.TOUCH, Capability.SWIPE, Capability.SCROLL)

    override fun isAvailable(): Boolean {
        val helper = AdbHelper.getInstance() ?: return false
        return helper.isConnected
    }

    fun sendKey(keyCode: Int): Boolean {
        val helper = AdbHelper.getInstance() ?: return false
        return helper.sendData(keyCode)
    }

    fun sendText(text: String): Boolean {
        val helper = AdbHelper.getInstance() ?: return false
        return helper.sendData(text)
    }

    fun tap(x: Int, y: Int): Boolean = AdbHelper.getInstance()?.sendShell("input tap $x $y") == true
    fun swipe(x: Int, y: Int, endX: Int, endY: Int, duration: Int): Boolean =
        AdbHelper.getInstance()?.sendShell("input swipe $x $y $endX $endY $duration") == true
}
