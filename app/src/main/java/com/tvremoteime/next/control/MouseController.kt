package com.tvremoteime.next.control

import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.KeyEvent
import com.tvremoteime.next.core.MainThread
import com.tvremoteime.next.mouse.MouseAccessibilityService

/** 光标坐标独立于辅助服务，Root/ADB 单独启用时也可以定位、点击。 */
class MouseController(private val context: Context) {
    private var x = -1
    private var y = -1

    @Synchronized
    fun position(dx: Int = 0, dy: Int = 0): IntArray {
        val metrics = DisplayMetrics()
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels.coerceAtLeast(1)
        val height = metrics.heightPixels.coerceAtLeast(1)
        if (x < 0) { x = width / 2; y = height / 2 }
        x = (x.toLong() + dx).coerceIn(0, (width - 1).toLong()).toInt()
        y = (y.toLong() + dy).coerceIn(0, (height - 1).toLong()).toInt()
        return intArrayOf(x, y, width, height)
    }

    fun move(dx: Int, dy: Int): IntArray = position(dx, dy).also {
        MouseAccessibilityService.getInstance()?.updateCursor(it[0], it[1])
    }

    fun click(button: Int): Boolean {
        if (button == 1) {
            if (MainThread.call { MouseAccessibilityService.getInstance()?.back() == true }) return true
            return ControlCenter.sendKey(KeyEvent.KEYCODE_BACK)
        }
        if (button !in 0..2) return false
        val p = position()
        return ControlCenter.router().execute(Capability.TOUCH) {
            when (it) {
                is AccessibilityChannel -> MainThread.call { MouseAccessibilityService.getInstance()?.tap(p[0], p[1]) == true }
                is RootChannel -> it.tap(p[0], p[1])
                is AdbChannel -> it.tap(p[0], p[1])
                else -> false
            }
        }
    }

    fun swipe(distance: Long, duration: Int = 200, capability: Capability = Capability.SWIPE): Boolean {
        val p = position()
        val endY = (p[1] + distance).coerceIn(0, (p[3] - 1).toLong()).toInt()
        return ControlCenter.router().execute(capability) {
            when (it) {
                is AccessibilityChannel -> MainThread.call { MouseAccessibilityService.getInstance()?.swipe(p[0], p[1], p[0], endY, duration) == true }
                is RootChannel -> it.swipe(p[0], p[1], p[0], endY, duration)
                is AdbChannel -> it.swipe(p[0], p[1], p[0], endY, duration)
                else -> false
            }
        }
    }
}
