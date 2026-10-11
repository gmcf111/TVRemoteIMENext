package com.tvremoteime.next.mouse

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.tvremoteime.next.adb.AdbHelper

/**
 * 辅助功能服务 - 触控板鼠标模拟（移植自 cacaview MouseAccessibilityService.java）
 *
 * 通道优先级（02 文档触控/手势域）：
 * - API 24+：dispatchGesture（本服务）
 * - 手势取消/低版本：AdbHelper shell:input 回退 → Runtime.exec 兜底
 */
class MouseAccessibilityService : AccessibilityService() {
    private var cursorOverlay: MouseCursorOverlay? = null
    private var mainHandler: Handler? = null

    private var mouseX = 0
    private var mouseY = 0
    private var screenWidth = 0
    private var screenHeight = 0

    private var hideCursorRunnable: Runnable? = null
    private var isCursorHidden = false

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "MouseAccessibilityService created")
        mainHandler = Handler(Looper.getMainLooper())
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "MouseAccessibilityService connected")

        initScreenSize()
        initCursorOverlay()

        // AdbHelper 作为低版本/手势取消的回退通道
        if (AdbHelper.getInstance() == null) {
            AdbHelper.createInstance()
        }
        AdbHelper.initService(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 不处理辅助功能事件（仅用手势与 overlay 能力）
    }

    override fun onInterrupt() {
        Log.i(TAG, "MouseAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        cursorOverlay?.hide()
        cursorOverlay = null
        hideCursorRunnable?.let { mainHandler?.removeCallbacks(it) }
        Log.i(TAG, "MouseAccessibilityService destroyed")
    }

    private fun initScreenSize() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        mouseX = screenWidth / 2
        mouseY = screenHeight / 2
        Log.i(TAG, "Screen size: ${screenWidth}x$screenHeight")
    }

    private fun initCursorOverlay() {
        mainHandler?.post {
            cursorOverlay = MouseCursorOverlay(this)
            cursorOverlay?.show()
            cursorOverlay?.updatePosition(mouseX, mouseY)
            hideCursorRunnable = Runnable { autoHideCursor() }
            resetHideTimer()
        }
    }

    private fun autoHideCursor() {
        if (cursorOverlay != null && cursorOverlay!!.isShowing()) {
            cursorOverlay?.hide()
            isCursorHidden = true
            Log.i(TAG, "Cursor auto-hidden due to inactivity")
        }
    }

    private fun resetHideTimer() {
        hideCursorRunnable?.let { mainHandler?.removeCallbacks(it) }
        if (isCursorHidden && cursorOverlay != null) {
            mainHandler?.post {
                cursorOverlay?.show()
                cursorOverlay?.updatePosition(mouseX, mouseY)
            }
            isCursorHidden = false
        }
        hideCursorRunnable?.let { mainHandler?.postDelayed(it, CURSOR_HIDE_DELAY) }
    }

    fun showCursor() {
        mainHandler?.post {
            cursorOverlay?.let {
                it.show()
                resetHideTimer()
            }
        }
    }

    fun hideCursor() {
        mainHandler?.post {
            cursorOverlay?.let {
                it.hide()
                isCursorHidden = true
                hideCursorRunnable?.let { r -> mainHandler?.removeCallbacks(r) }
            }
        }
    }

    /** 相对移动光标，返回新位置 [x, y] */
    fun moveMouse(dx: Int, dy: Int): IntArray {
        mouseX = (mouseX + dx).coerceIn(0, if (screenWidth > 0) screenWidth - 1 else 0)
        mouseY = (mouseY + dy).coerceIn(0, if (screenHeight > 0) screenHeight - 1 else 0)
        mainHandler?.post { cursorOverlay?.updatePosition(mouseX, mouseY) }
        resetHideTimer()
        return intArrayOf(mouseX, mouseY)
    }

    /** button: 0=左键, 1=右键(返回), 2=中键 */
    fun click(button: Int): Boolean {
        resetHideTimer()
        return if (button == 1) {
            performGlobalAction(GLOBAL_ACTION_BACK)
        } else {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                Log.e(TAG, "dispatchGesture requires API 24+")
                false
            } else {
                performClick(mouseX, mouseY)
            }
        }
    }

    /** dy: 正数向下，负数向上 */
    fun scroll(dy: Int): Boolean {
        resetHideTimer()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.e(TAG, "dispatchGesture requires API 24+")
            return false
        }
        val scrollAmount = dy * 50
        val endY = (mouseY + scrollAmount).coerceIn(0, screenHeight - 1)
        return performSwipe(mouseX, mouseY, mouseX, endY, 200)
    }

    private fun performClick(x: Int, y: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return performClickViaShell(x, y)
        }
        val clickPath = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(clickPath, 0, 50L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Click completed at $x,$y")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Click cancelled at $x,$y, trying shell fallback")
                performClickViaShell(x, y)
            }
        }, null)
    }

    private fun performClickViaShell(x: Int, y: Int): Boolean {
        val adbHelper = AdbHelper.getInstance()
        if (adbHelper != null && adbHelper.isRunning) {
            adbHelper.sendData("shell:input tap $x $y")
            return true
        }
        return try {
            Runtime.getRuntime().exec("input tap $x $y")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Shell click failed", e)
            false
        }
    }

    private fun performLongClick(x: Int, y: Int, duration: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return performLongClickViaShell(x, y, duration)
        }
        val clickPath = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(clickPath, 0, duration.toLong())
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Long click completed at $x,$y")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Long click cancelled at $x,$y, trying shell fallback")
                performLongClickViaShell(x, y, duration)
            }
        }, null)
    }

    private fun performLongClickViaShell(x: Int, y: Int, duration: Int): Boolean {
        val adbHelper = AdbHelper.getInstance()
        if (adbHelper != null && adbHelper.isRunning) {
            adbHelper.sendData("shell:input swipe $x $y $x $y $duration")
            return true
        }
        return try {
            Runtime.getRuntime().exec("input swipe $x $y $x $y $duration")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Shell long click failed", e)
            false
        }
    }

    private fun performSwipe(startX: Int, startY: Int, endX: Int, endY: Int, duration: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val swipePath = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }
        val stroke = GestureDescription.StrokeDescription(swipePath, 0, duration.toLong())
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Swipe completed")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Swipe cancelled")
            }
        }, null)
    }

    fun getMousePosition(): IntArray = intArrayOf(mouseX, mouseY)

    fun swipeUp(distance: Int): Boolean {
        resetHideTimer()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return performSwipeViaShell(mouseX, mouseY, mouseX, mouseY - distance)
        }
        val endY = (mouseY - distance).coerceAtLeast(0)
        return performSwipeWithFallback(mouseX, mouseY, mouseX, endY, 200)
    }

    fun swipeDown(distance: Int): Boolean {
        resetHideTimer()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return performSwipeViaShell(mouseX, mouseY, mouseX, mouseY + distance)
        }
        val endY = (mouseY + distance).coerceAtMost(screenHeight - 1)
        return performSwipeWithFallback(mouseX, mouseY, mouseX, endY, 200)
    }

    fun longClick(duration: Int): Boolean {
        resetHideTimer()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return performLongClickViaShell(mouseX, mouseY, duration)
        }
        return performLongClick(mouseX, mouseY, duration)
    }

    private fun performSwipeWithFallback(startX: Int, startY: Int, endX: Int, endY: Int, duration: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return performSwipeViaShell(startX, startY, endX, endY)
        }
        val swipePath = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }
        val stroke = GestureDescription.StrokeDescription(swipePath, 0, duration.toLong())
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Swipe completed from $startX,$startY to $endX,$endY")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Swipe cancelled, trying shell fallback")
                performSwipeViaShell(startX, startY, endX, endY)
            }
        }, null)
    }

    private fun performSwipeViaShell(startX: Int, startY: Int, endX: Int, endY: Int): Boolean {
        val adbHelper = AdbHelper.getInstance()
        if (adbHelper != null && adbHelper.isRunning) {
            adbHelper.sendData("shell:input swipe $startX $startY $endX $endY 200")
            return true
        }
        return try {
            Runtime.getRuntime().exec("input swipe $startX $startY $endX $endY 200")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Shell swipe failed", e)
            false
        }
    }

    fun resetMousePosition() {
        mouseX = screenWidth / 2
        mouseY = screenHeight / 2
        mainHandler?.post { cursorOverlay?.updatePosition(mouseX, mouseY) }
    }

    companion object {
        private const val TAG = "MouseAccessibility"
        private const val CURSOR_HIDE_DELAY = 5000L

        @Volatile
        private var instance: MouseAccessibilityService? = null

        fun getInstance(): MouseAccessibilityService? = instance

        fun isServiceEnabled(): Boolean = instance != null
    }
}
