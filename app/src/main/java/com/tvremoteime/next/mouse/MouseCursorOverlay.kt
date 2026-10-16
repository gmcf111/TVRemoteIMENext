package com.tvremoteime.next.mouse

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import com.tvremoteime.next.R

/**
 * 鼠标光标悬浮窗（移植自 cacaview MouseCursorOverlay.java）
 *
 * API 26+：TYPE_ACCESSIBILITY_OVERLAY（随辅助服务权限）
 * API 23-25：TYPE_SYSTEM_OVERLAY；API 19-22：TYPE_SYSTEM_ALERT（需 SYSTEM_ALERT_WINDOW）
 */
class MouseCursorOverlay(private val context: Context) {
    private var windowManager: WindowManager? = null
    private var cursorView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isShowing = false

    // 光标尖端相对图标左上角的偏移
    private var hotspotX = 0
    private var hotspotY = 0

    init {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        initCursorView()
    }

    private fun initCursorView() {
        val imageView = androidx.appcompat.widget.AppCompatImageView(context)
        imageView.setImageResource(R.drawable.mouse_cursor)
        cursorView = imageView

        val windowType = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 ->
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
            else -> WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = 0
            y = 0
        }
    }

    fun show() {
        if (!isShowing && cursorView != null) {
            try {
                windowManager?.addView(cursorView, layoutParams)
                isShowing = true
                Log.i(TAG, "Cursor overlay shown")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show cursor overlay", e)
            }
        }
    }

    fun hide() {
        if (isShowing && cursorView != null) {
            try {
                windowManager?.removeView(cursorView)
                isShowing = false
                Log.i(TAG, "Cursor overlay hidden")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to hide cursor overlay", e)
            }
        }
    }

    fun updatePosition(x: Int, y: Int) {
        if (isShowing && cursorView != null) {
            layoutParams?.x = x - hotspotX
            layoutParams?.y = y - hotspotY
            try {
                windowManager?.updateViewLayout(cursorView, layoutParams)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update cursor position", e)
            }
        }
    }

    fun setHotspot(x: Int, y: Int) {
        hotspotX = x
        hotspotY = y
    }

    fun isShowing(): Boolean = isShowing

    companion object {
        private const val TAG = "MouseCursorOverlay"
    }
}
