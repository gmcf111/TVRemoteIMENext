package com.tvremoteime.next.core

import android.content.Context
import android.os.Handler
import android.provider.Settings
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.Toast

/**
 * 全局环境配置与工具（对应旧版 Environment.java）
 */
object Env {
    const val TAG = "TVRemoteIMENext"
    const val NEED_DEBUG = false

    var adbServerPort = 5555

    const val ACTION = "com.tvremoteime.next"

    private var toastHandler: Handler? = null

    fun debug(msg: String, tr: Throwable? = null) {
        if (NEED_DEBUG) Log.d(TAG, msg, tr)
    }

    fun initToastHandler() {
        if (toastHandler == null) toastHandler = Handler(android.os.Looper.getMainLooper())
    }

    fun toastInHandler(context: Context?, msg: String) {
        toastHandler?.post { context?.let { toast(it, msg) } }
    }

    fun toast(context: Context, msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    }

    /** 本应用输入法是否已在系统设置中激活启用 */
    fun isEnableIME(context: Context): Boolean = try {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.enabledInputMethodList.any { it.packageName == context.packageName }
    } catch (e: Exception) {
        false
    }

    /** 本应用输入法是否为系统默认输入法 */
    fun isDefaultIME(context: Context): Boolean = try {
        val defaultIme = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD
        )
        defaultIme != null && android.content.ComponentName.unflattenFromString(defaultIme)?.packageName == context.packageName
    } catch (e: Exception) {
        false
    }
}
