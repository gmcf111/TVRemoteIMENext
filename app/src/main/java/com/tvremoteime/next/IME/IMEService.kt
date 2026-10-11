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
import com.tvremoteime.next.adb.AdbHelper
import com.tvremoteime.next.core.Env
import com.tvremoteime.next.core.KeyEventUtils
import com.tvremoteime.next.core.QRCodeGen
import com.tvremoteime.next.core.AppPackagesHelper
import com.tvremoteime.next.dlna.DLNAUtils
import com.tvremoteime.next.server.RemoteServer
import com.tvremoteime.next.server.ServerFileManager
import com.tvremoteime.next.update.AutoUpdateManager
import java.io.IOException

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

    private var helpDialog: View? = null
    private var qrCodeImage: android.widget.ImageView? = null
    private var addressView: TextView? = null

    private var mServer: RemoteServer? = null
    private var qweLine: LinearLayout? = null
    private var asdLine: LinearLayout? = null
    private var zxcLine: LinearLayout? = null

    private val handler = Handler()

    override fun onCreate() {
        super.onCreate()
        Env.initToastHandler()
        com.tvremoteime.next.control.ControlCenter.init(this)
        com.tvremoteime.next.control.ImeChannel.imeAvailable = true
        com.tvremoteime.next.control.ImeChannel.imeTextHook = { text -> commitText(text) }
        com.tvremoteime.next.control.ImeChannel.imeKeyHook = { keyCode ->
            sendKeyCode(keyCode)
            true
        }
        ServerFileManager.resetBaseDir(this)
        startRemoteServer()
        DLNAUtils.startDLNAService(applicationContext)
        AutoUpdateManager(this, handler)
    }

    override fun onCreateInputView(): View {
        mInputView = layoutInflater.inflate(R.layout.keyboard, null) as RelativeLayout

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

    private fun isSendToAdbService(data: Any): Boolean {
        if (AdbHelper.initService(applicationContext)) {
            AdbHelper.getInstance()?.sendData(data)
            return true
        }
        return false
    }

    private fun startRemoteServer() {
        do {
            mServer = RemoteServer(RemoteServer.serverPort, this)
            mServer?.dataReceiver = object : RemoteServer.DataReceiver {
                override fun onKeyEventReceived(keyCode: String, keyAction: Int) {
                    if ("cls".equals(keyCode, ignoreCase = true)) {
                        val ic = currentInputConnection
                        ic?.deleteSurroundingText(Int.MAX_VALUE, Int.MAX_VALUE)
                    } else {
                        val kc = KeyEvent.keyCodeFromString(keyCode)
                        if (kc != KeyEvent.KEYCODE_UNKNOWN) {
                            if (mInputView != null && KeyEventUtils.isKeyboardFocusEvent(kc) && mInputView!!.isShown) {
                                if (keyAction == KEY_ACTION_PRESSED || keyAction == KEY_ACTION_DOWN) {
                                    handler.post {
                                        if (!handleKeyboardFocusEvent(kc)) {
                                            if (!isSendToAdbService(kc)) sendKeyCode(kc)
                                        }
                                    }
                                }
                            } else {
                                val eventTime = SystemClock.uptimeMillis()
                                val ic = currentInputConnection
                                when (keyAction) {
                                    KEY_ACTION_PRESSED -> {
                                        // 02 文档按键域优先级：Root → IME → ADB
                                        val root = com.tvremoteime.next.control.ControlCenter.root()
                                        if (root != null && root.isAvailable()) {
                                            root.sendKey(kc)
                                        } else if (!isSendToAdbService(kc)) {
                                            sendKeyCode(kc)
                                        }
                                    }
                                    KEY_ACTION_DOWN -> {
                                        if (!isSendToAdbService(kc) && ic != null) {
                                            ic.sendKeyEvent(
                                                KeyEvent(
                                                    eventTime, eventTime, KeyEvent.ACTION_DOWN, kc, 0, 0,
                                                    KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                                                    KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
                                                )
                                            )
                                        }
                                    }
                                    KEY_ACTION_UP -> {
                                        ic?.sendKeyEvent(
                                            KeyEvent(
                                                eventTime, eventTime, KeyEvent.ACTION_UP, kc, 0, 0,
                                                KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                                                KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                override fun onTextReceived(text: String) {
                    // 02 文档文字域：IME 激活时优先 IME（最快），否则 Root，最后 ADB
                    if (!commitText(text)) {
                        val root = com.tvremoteime.next.control.ControlCenter.root()
                        if (root != null && root.isAvailable()) {
                            root.sendText(text)
                        } else {
                            isSendToAdbService(text)
                        }
                    }
                }
            }
            try {
                mServer?.start()
                Env.toastInHandler(this, getString(R.string.app_name) + "远程服务已启动")
                Log.i(TAG, "远程服务创建成功！port=${RemoteServer.serverPort}")
                break
            } catch (ex: IOException) {
                Log.e(TAG, "建立输入HTTP服务时出错", ex)
                RemoteServer.serverPort++
                mServer?.stop()
            }
        } while (RemoteServer.serverPort < 9999)
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
        com.tvremoteime.next.control.ImeChannel.imeAvailable = false
        com.tvremoteime.next.control.ImeChannel.imeTextHook = null
        com.tvremoteime.next.control.ImeChannel.imeKeyHook = null
        if (mServer != null && mServer!!.starting) {
            Log.i(TAG, "远程输入服务已停止！")
            mServer?.stop()
        }
        DLNAUtils.stopDLNAService()
        AdbHelper.stopService()
        Env.toastInHandler(this, getString(R.string.app_name) + "服务已停止")
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
        when (v.id) {
            R.id.btnCaps -> v.setBackgroundResource(if (capsOn) R.drawable.key_pressed_on else R.drawable.key_pressed_off)
            R.id.btnClose -> {
                hideWindowByKey = true
                finishInput()
                return
            }
            else -> v.setBackgroundResource(R.drawable.key_pressed)
        }
        clickButton(v, false)
        handler.postDelayed({
            when {
                v === btnCaps -> v.setBackgroundResource(if (capsOn) R.drawable.key_on else R.drawable.key_off)
                else -> v.setBackgroundResource(R.drawable.key)
            }
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
            btnCaps?.setBackgroundResource(if (capsOn) R.drawable.key_on else R.drawable.key_off)
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
        val server = mServer ?: return
        if (addressView?.text?.isEmpty() == true) {
            val version = AppPackagesHelper.getCurrentPackageVersion(this)
            val title = helpDialog?.findViewById<TextView>(R.id.title)
            title?.text = title?.text.toString() + " " + version
            val address = server.getServerAddress()
            addressView?.text = address
            qrCodeImage?.setImageBitmap(QRCodeGen.generateBitmap(address, 300, 300))
        }
        helpDialog?.visibility = View.VISIBLE
    }
}
