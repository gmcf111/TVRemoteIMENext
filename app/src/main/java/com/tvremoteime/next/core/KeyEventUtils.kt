package com.tvremoteime.next.core

import android.view.KeyEvent

/**
 * 按键事件工具（对应旧版 KeyEventUtils.java）
 */
object KeyEventUtils {
    /** 这些按键用于软键盘内的焦点导航， intercepted 后不发给应用 */
    fun isKeyboardFocusEvent(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_CAPS_LOCK,
        KeyEvent.KEYCODE_ESCAPE,
        KeyEvent.KEYCODE_BACK -> true
        else -> false
    }
}
