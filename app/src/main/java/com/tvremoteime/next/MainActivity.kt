package com.tvremoteime.next

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import com.tvremoteime.next.adb.AdbHelper
import com.tvremoteime.next.core.AppPackagesHelper
import com.tvremoteime.next.core.Env
import com.tvremoteime.next.core.QRCodeGen
import com.tvremoteime.next.dlna.DLNAUtils
import com.tvremoteime.next.IME.IMEService
import com.tvremoteime.next.server.RemoteServer

/**
 * 主界面（对应旧版 MainActivity.java）
 * 阶段 5 将重做为 MD3 卡片布局（05 文档）；本阶段保持功能对等。
 */
class MainActivity : Activity(), View.OnClickListener {

    private var qrCodeImage: ImageView? = null
    private var addressView: TextView? = null
    private var dlnaNameText: EditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        qrCodeImage = findViewById(R.id.ivQRCode)
        addressView = findViewById(R.id.tvAddress)
        dlnaNameText = findViewById(R.id.etDLNAName)

        findViewById<View>(R.id.btnUseIME).setOnClickListener(this)
        findViewById<View>(R.id.btnSetIME).setOnClickListener(this)
        findViewById<View>(R.id.btnStartService).setOnClickListener(this)
        findViewById<View>(R.id.btnSetDLNA).setOnClickListener(this)

        title = resources.getString(R.string.app_name) + "  V" + AppPackagesHelper.getCurrentPackageVersion(this)
        dlnaNameText?.setText(DLNAUtils.getDLNANameSuffix(applicationContext))

        refreshQRCode()
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.btnUseIME -> {
                openInputMethodSettings()
                if (Env.isEnableIME(this)) {
                    Env.toast(applicationContext, "太棒了，您已经激活启用了" + getString(R.string.keyboard_name) + "输入法！")
                }
            }
            R.id.btnSetIME -> {
                if (!Env.isEnableIME(this)) {
                    Env.toast(applicationContext, "抱歉，请您先激活启用" + getString(R.string.keyboard_name) + "输入法！")
                    openInputMethodSettings()
                    if (!Env.isEnableIME(this)) return
                }
                try {
                    (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
                } catch (ignored: Exception) {
                    Env.toast(applicationContext, "抱歉，无法设置为系统默认输入法，请手动启动服务！")
                }
                if (Env.isDefaultIME(this)) {
                    Env.toast(applicationContext, "太棒了，" + getString(R.string.keyboard_name) + "已是系统默认输入法！")
                }
            }
            R.id.btnStartService -> {
                startService(Intent(IMEService.ACTION))
                if (!Env.isDefaultIME(this)) {
                    if (AdbHelper.getInstance() == null) AdbHelper.createInstance()
                }
                Env.toast(applicationContext, "服务已手动启动，稍后可尝试访问控制端页面")
            }
            R.id.btnSetDLNA -> {
                dlnaNameText?.text?.toString()?.let {
                    DLNAUtils.setDLNANameSuffix(applicationContext, it)
                }
            }
        }
        refreshQRCode()
    }

    private fun openInputMethodSettings() {
        try {
            startActivityForResult(Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS), 0)
        } catch (ignored: Exception) {
            Env.toast(applicationContext, "抱歉，无法激活启用输入法，请手动启动服务！")
        }
    }

    private fun refreshQRCode() {
        val address = RemoteServer.getServerAddress(this)
        addressView?.text = address
        qrCodeImage?.setImageBitmap(QRCodeGen.generateBitmap(address, 150, 150))
    }
}
