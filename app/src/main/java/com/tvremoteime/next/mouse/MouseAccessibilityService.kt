package com.tvremoteime.next.mouse

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.tvremoteime.next.control.ControlCenter
import com.tvremoteime.next.server.RemoteControlService

/** 仅负责辅助功能手势与光标显示，Root/ADB 回退统一交给控制中心。 */
class MouseAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var overlay: MouseCursorOverlay? = null
    private val hide = Runnable { overlay?.hide() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        ControlCenter.init(this)
        overlay = MouseCursorOverlay(this)
        showCursor()
        RemoteControlService.start(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        instance = null
        handler.removeCallbacksAndMessages(null)
        overlay?.hide()
        overlay = null
        super.onDestroy()
    }

    fun updateCursor(x: Int, y: Int) {
        handler.post {
            overlay?.show()
            overlay?.updatePosition(x, y)
            handler.removeCallbacks(hide)
            handler.postDelayed(hide, 5000)
        }
    }

    fun showCursor() {
        val p = ControlCenter.mouse().position()
        updateCursor(p[0], p[1])
    }

    fun hideCursor() {
        handler.post { handler.removeCallbacks(hide); overlay?.hide() }
    }

    fun back(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun tap(x: Int, y: Int): Boolean = gesture(x, y, x, y, 50)
    fun swipe(x: Int, y: Int, endX: Int, endY: Int, duration: Int): Boolean = gesture(x, y, endX, endY, duration)

    private fun gesture(x: Int, y: Int, endX: Int, endY: Int, duration: Int): Boolean {
        if (Build.VERSION.SDK_INT < 24) return false
        updateCursor(x, y)
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
            if (x != endX || y != endY) lineTo(endX.toFloat(), endY.toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration.toLong())).build()
        // 已接受的手势可能被下一次操作取消；取消时重放会产生额外点击。
        return dispatchGesture(gesture, null, handler)
    }

    companion object {
        @Volatile private var instance: MouseAccessibilityService? = null
        fun getInstance(): MouseAccessibilityService? = instance
        fun isServiceEnabled(): Boolean = instance != null
    }
}
