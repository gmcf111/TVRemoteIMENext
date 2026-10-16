package com.tvremoteime.next.control

import android.os.Build
import com.tvremoteime.next.mouse.MouseAccessibilityService

/**
 * 辅助功能通道（02 文档）
 *
 * 能力：TOUCH/SWIPE/SCROLL（API 24+ dispatchGesture）
 * 可用条件：用户启用辅助功能且服务已连接
 */
class AccessibilityChannel : ControlChannel {

    override val id: String = "a11y"
    override val capabilities: Set<Capability> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            setOf(Capability.TOUCH, Capability.SWIPE, Capability.SCROLL)
        } else {
            emptySet() // <7.0 无手势能力，直接回退 Root/ADB
        }

    override fun isAvailable(): Boolean = MouseAccessibilityService.isServiceEnabled()

}
