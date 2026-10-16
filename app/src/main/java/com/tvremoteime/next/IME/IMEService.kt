package com.tvremoteime.next.IME

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import com.tvremoteime.next.R
import com.tvremoteime.next.core.Env
import com.tvremoteime.next.core.KeyEventUtils
import com.tvremoteime.next.core.QRCodeGen
import com.tvremoteime.next.core.AppPackagesHelper
import com.tvremoteime.next.server.RemoteServer

/**
 * 输入法服务（对应旧版 IMEService.java）
 *
 * 冷启动红线 ≤300ms：键盘视图保持轻量 View，不引入组件库（01 文档非功能需求）。
 */
class IMEService : InputMethodService(), View.OnClickListener {

    companion object {
        const val TAG = "TVRemoteIMENext"
        const val ACTION = "com.tvremoteime.next"

        const val KEY_ACTION_PRESSED = 0
        const val KEY_ACTION_DOWN = 1
        const val KEY_ACTION_UP = 2
    }

    private var capsOn = false
    private var btnCaps: ImageButton? = null
    private var focusedView: View? = null
    private var mInputView: RelativeLayout? = null
    private var hideWindowByKey = false

    // 主题色动态按键背景缓存（onCreateInputView 时构建）
    private var keyModernBg: android.graphics.drawable.Drawable? = null
    private var keyShiftOnBg: android.graphics.drawable.Drawable? = null
    private var keyShiftOffBg: android.graphics.drawable.Drawable? = null

    private var helpDialog: View? = null
    private var qrCodeImage: android.widget.ImageView? = null
    private var addressView: TextView? = null

    private var qweLine: LinearLayout? = null
    private var asdLine: LinearLayout? = null
    private var zxcLine: LinearLayout? = null

    private val handler = Handler()
    private val themeListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        handler.post { applyKeyboardTheme() }
    }

    override fun onCreate() {
        super.onCreate()
        Env.initToastHandler()
        com.tvremoteime.next.theme.ThemePrefs.prefs(this).registerOnSharedPreferenceChangeListener(themeListener)
        com.tvremoteime.next.control.ControlCenter.init(this)
        com.tvremoteime.next.control.ImeChannel.imeAvailable = true
        com.tvremoteime.next.control.ImeChannel.imeTextHook = { text ->
            com.tvremoteime.next.core.MainThread.call { commitText(text) }
        }
        com.tvremoteime.next.control.ImeChannel.imeKeyHook = { keyCode ->
            com.tvremoteime.next.core.MainThread.call {
                if (currentInputConnection == null && keyCode != KeyEvent.KEYCODE_HOME) false else { sendKeyCode(keyCode); true }
            }
        }
        com.tvremoteime.next.control.ImeChannel.imeDispatchHook = { code, action ->
            com.tvremoteime.next.core.MainThread.call { handleRemoteKey(code, action) }
        }
        com.tvremoteime.next.server.RemoteControlService.start(this)
    }

    override fun onCreateInputView(): View {
        mInputView = layoutInflater.inflate(R.layout.keyboard, null) as RelativeLayout
        focusedView = null
        fun bindButtons(group: android.view.ViewGroup) {
            for (i in 0 until group.childCount) {
                val child = group.getChildAt(i)
                if (child is Button || child is ImageButton) child.setOnClickListener(this)
                if (child is android.view.ViewGroup) bindButtons(child)
            }
        }
        bindButtons(mInputView!!)

        // 主题色跟随（04 文档 5.1）：一次构建动态 drawable 并缓存，键背景全部替换
        applyKeyboardTheme()

        capsOn = true
        btnCaps = mInputView?.findViewById(R.id.btnCaps)
        qweLine = mInputView?.findViewById(R.id.qweLine)
        asdLine = mInputView?.findViewById(R.id.asdLine)
        zxcLine = mInputView?.findViewById(R.id.zxcLine)

        helpDialog = mInputView?.findViewById(R.id.helpDialog)
        qrCodeImage = helpDialog?.findViewById(R.id.ivQRCode)
        addressView = helpDialog?.findViewById(R.id.tvAddress)

        toggleCapsState(true)

        return mInputView!!
    }

    /** 键盘主题化：按当前主题色生成三套键背景，遍历替换（含 Shift/功能键） */
    private fun applyKeyboardTheme() {
        val view = mInputView ?: return
        val modern = com.tvremoteime.next.theme.KeyboardDrawableFactory.createModernKey(this)
        val action = com.tvremoteime.next.theme.KeyboardDrawableFactory.createActionKey(this)
        val shiftOn = com.tvremoteime.next.theme.KeyboardDrawableFactory.createShiftOnKey(this)
        val shiftOff = com.tvremoteime.next.theme.KeyboardDrawableFactory.createShiftOffKey(this)
        // 缓存给 clickButtonByKey / toggleCapsState 复用
        keyModernBg = modern
        keyShiftOnBg = shiftOn
        keyShiftOffBg = shiftOff

        val actionIds = setOf(R.id.btnDelete, R.id.btnEnter, R.id.btnSpace, R.id.btnHelp, R.id.btnClose)
        for (i in 0 until view.childCount) {
            val row = view.getChildAt(i) as? android.view.ViewGroup ?: continue
            for (j in 0 until row.childCount) {
                val child = row.getChildAt(j) ?: continue
                when (child.id) {
                    R.id.btnCaps -> child.background = (if (capsOn) shiftOn else shiftOff).constantState!!.newDrawable().mutate()
                    in actionIds -> child.background = action.constantState!!.newDrawable().mutate()
                    else -> if (child is Button || child is ImageButton) child.background = modern.constantState!!.newDrawable().mutate()
                }
                if (child is Button) child.setTextColor(if (child.id in actionIds) android.graphics.Color.WHITE else android.graphics.Color.rgb(30, 30, 30))
            }
        }
        // 帮助弹窗嵌套层（helpDialog 内无按键）
        // 键盘容器背景：主题色调浅色（替代布局里写死的 #444）
        val accent = com.tvremoteime.next.theme.ThemePrefs.getAccentColor(this) ?: 0xFF0061A4.toInt()
        run {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(accent, hsv)
            hsv[1] *= 0.18f
            hsv[2] = 0.93f
            view.setBackgroundColor(android.graphics.Color.HSVToColor(hsv))
        }
    }

    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        if (hideWindowByKey) {
            hideWindowByKey = false
            return false
        }
        val editorInfo = currentInputEditorInfo
        return !(editorInfo == null || editorInfo.inputType == EditorInfo.TYPE_NULL)
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    private val remoteKeyDownTimes = mutableMapOf<Int, Long>()

    private fun handleRemoteKey(code: String, action: Int): Boolean {
        if (code.equals("cls", true)) return currentInputConnection?.deleteSurroundingText(Int.MAX_VALUE, Int.MAX_VALUE) == true
        val key = KeyEvent.keyCodeFromString(code)
        if (key == KeyEvent.KEYCODE_UNKNOWN) return false
        if (mInputView?.isShown == true && KeyEventUtils.isKeyboardFocusEvent(key)) {
            return action == KEY_ACTION_UP || handleKeyboardFocusEvent(key)
        }
        if (action == KEY_ACTION_PRESSED) return false
        if (action == KEY_ACTION_DOWN && com.tvremoteime.next.control.ControlCenter.root()?.isAvailable() == true) return false
        val connection = currentInputConnection ?: return false
        val now = SystemClock.uptimeMillis()
        val down = if (action == KEY_ACTION_DOWN) remoteKeyDownTimes.getOrPut(key) { now }
            else remoteKeyDownTimes.remove(key) ?: return false
        val sent = connection.sendKeyEvent(KeyEvent(down, now,
            if (action == KEY_ACTION_DOWN) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP,
            key, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
            KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE))
        if (!sent) remoteKeyDownTimes.remove(key)
        return sent
    }

    private fun commitText(text: String): Boolean {
        val ic = currentInputConnection
        var flag = false
        if (ic != null) {
            Env.debug("commitText:$text")
            if (text.length > 1 && ic.beginBatchEdit()) {
                flag = ic.commitText(text, 1)
                ic.endBatchEdit()
            } else {
                flag = ic.commitText(text, 1)
            }
        }
        return flag
    }

    private fun sendKeyCode(keyCode: Int) {
        Env.debug("send-key-code:$keyCode")
        if (keyCode == KeyEvent.KEYCODE_HOME) {
            // 拦截 HOME 键
            val i = Intent(Intent.ACTION_MAIN).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addCategory(Intent.CATEGORY_HOME)
            }
            startActivity(i)
        } else {
            sendDownUpKeyEvents(keyCode)
        }
    }

    override fun onDestroy() {
        com.tvremoteime.next.theme.ThemePrefs.prefs(this).unregisterOnSharedPreferenceChangeListener(themeListener)
        com.tvremoteime.next.control.ImeChannel.imeAvailable = false
        com.tvremoteime.next.control.ImeChannel.imeTextHook = null
        com.tvremoteime.next.control.ImeChannel.imeKeyHook = null
        com.tvremoteime.next.control.ImeChannel.imeDispatchHook = null
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (handleKeyboardFocusEvent(keyCode)) return true
        Env.debug("keydown-event:$keyCode")
        // 同步软键盘大小写状态：不处理以下按键则可能与软键盘状态不同步
        if (keyCode == KeyEvent.KEYCODE_CAPS_LOCK) capsOn = !capsOn
        if (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
            if (commitText((keyCode - KeyEvent.KEYCODE_0).toString())) return true
        } else if (keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) {
            if (commitText(((if (capsOn) 65 else 97) + keyCode - KeyEvent.KEYCODE_A).toChar().toString())) return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun handleKeyboardFocusEvent(keyCode: Int): Boolean {
        val inputView = mInputView ?: return false
        Env.debug("handleKeyboardFocusEvent:$keyCode")
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (inputView.isShown) {
                    requestNextButtonFocus(keyCode)
                    return true
                }
            }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (inputView.isShown && focusedView != null) {
                    clickButtonByKey(focusedView!!)
                    return true
                }
            }
            KeyEvent.KEYCODE_CAPS_LOCK -> {
                toggleCapsState(true)
                return true
            }
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BACK -> {
                if (inputView.isShown) {
                    if (helpDialog != null && helpDialog!!.isShown) {
                        helpDialog?.visibility = View.GONE
                    } else {
                        finishInput()
                    }
                    return true
                }
            }
        }
        return false
    }

    private fun requestNextButtonFocus(keyCode: Int) {
        val inputView = mInputView ?: return
        if (focusedView == null) {
            focusedView = (inputView.getChildAt(0) as LinearLayout).getChildAt(0)
        } else {
            var container = focusedView!!.parent as LinearLayout
            var rootInde = inputView.indexOfChild(container)
            var index = container.indexOfChild(focusedView)
            val isLasted = container.childCount == index + 1
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    rootInde--
                    if (rootInde < 0) rootInde = inputView.childCount - 2
                    container = inputView.getChildAt(rootInde) as LinearLayout
                    if (index >= container.childCount) index = if (isLasted) container.childCount - 1 else 0
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    rootInde++
                    if (rootInde >= inputView.childCount - 1) rootInde = 0
                    container = inputView.getChildAt(rootInde) as LinearLayout
                    if (index >= container.childCount) index = if (isLasted) container.childCount - 1 else 0
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    index--
                    if (index < 0) {
                        rootInde--
                        if (rootInde < 0) rootInde = inputView.childCount - 2
                        container = inputView.getChildAt(rootInde) as LinearLayout
                        index = container.childCount - 1
                    }
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    index++
                    if (index >= container.childCount) {
                        rootInde++
                        if (rootInde >= inputView.childCount - 1) rootInde = 0
                        container = inputView.getChildAt(rootInde) as LinearLayout
                        index = 0
                    }
                }
            }
            focusedView = container.getChildAt(index)
        }
        focusedView?.requestFocus()
        focusedView?.requestFocusFromTouch()
    }

    private fun finishInput() {
        onFinishInput()
        requestHideSelf(0)
    }

    private fun clickButtonByKey(v: View) {
        if (v.id == R.id.btnClose) {
            hideWindowByKey = true
            finishInput()
            return
        }
        v.isPressed = true
        clickButton(v, true)
        handler.postDelayed({
            v.isPressed = false
            v.requestFocus()
        }, 200)
    }

    private fun clickButton(v: View, resetCapsButtonState: Boolean) {
        when (v) {
            is Button -> {
                if (v.id == R.id.btnClose) {
                    finishInput()
                } else {
                    commitText(v.text.toString())
                }
            }
            is ImageButton -> {
                when (v.id) {
                    R.id.btnEnter -> sendKeyCode(KeyEvent.KEYCODE_ENTER)
                    R.id.btnSpace -> sendKeyCode(KeyEvent.KEYCODE_SPACE)
                    R.id.btnDelete -> sendKeyCode(KeyEvent.KEYCODE_DEL)
                    R.id.btnCaps -> toggleCapsState(resetCapsButtonState)
                    R.id.btnHelp -> showHelpDialog()
                }
            }
        }
    }

    override fun onClick(v: View) {
        clickButton(v, true)
        if (v.id != R.id.btnClose) {
            v.requestFocusFromTouch()
            focusedView = v
        }
    }

    private fun toggleCapsState(resetCapsButtonState: Boolean) {
        capsOn = !capsOn
        if (resetCapsButtonState)
            (if (capsOn) keyShiftOnBg else keyShiftOffBg)?.let { btnCaps?.background = it.constantState!!.newDrawable().mutate() }
        resetButtonChar(qweLine)
        resetButtonChar(asdLine)
        resetButtonChar(zxcLine)
    }

    private fun resetButtonChar(layout: LinearLayout?) {
        layout ?: return
        for (i in 0 until layout.childCount) {
            val v = layout.getChildAt(i)
            if (v is Button) {
                v.text = if (capsOn) v.text.toString().uppercase() else v.text.toString().lowercase()
            }
        }
    }

    private fun showHelpDialog() {
        if (addressView?.text?.isEmpty() == true) {
            val version = AppPackagesHelper.getCurrentPackageVersion(this)
            val title = helpDialog?.findViewById<TextView>(R.id.title)
            title?.text = title?.text.toString() + " " + version
            val address = RemoteServer.getServerAddress(this)
            addressView?.text = address
            qrCodeImage?.setImageBitmap(QRCodeGen.generateBitmap(address, 300, 300))
        }
        helpDialog?.visibility = View.VISIBLE
    }
}
