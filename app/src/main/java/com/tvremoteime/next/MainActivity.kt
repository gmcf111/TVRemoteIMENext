package com.tvremoteime.next

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import com.tvremoteime.next.adb.AdbHelper
import com.tvremoteime.next.control.ControlCenter
import com.tvremoteime.next.control.RootPrefs
import com.tvremoteime.next.core.AppPackagesHelper
import com.tvremoteime.next.core.Env
import com.tvremoteime.next.core.QRCodeGen
import com.tvremoteime.next.core.StorageHelper
import com.tvremoteime.next.dlna.DLNAUtils
import com.tvremoteime.next.IME.IMEService
import com.tvremoteime.next.mouse.MouseAccessibilityService
import com.tvremoteime.next.server.RemoteServer
import com.tvremoteime.next.theme.ThemeHelper
import com.tvremoteime.next.theme.ThemePrefs

/**
 * 主界面（阶段 5 MD3 重做，04 文档第二章布局）
 */
class MainActivity : Activity(), View.OnClickListener {

    private var qrCodeImage: ImageView? = null
    private var addressView: TextView? = null
    private var dlnaNameText: EditText? = null
    private var serviceBadge: TextView? = null
    private var accentPreview: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // 主题色叠加（04 文档 5.2：setContentView 之前）
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        qrCodeImage = findViewById(R.id.ivQRCode)
        addressView = findViewById(R.id.tvAddress)
        dlnaNameText = findViewById(R.id.etDLNAName)
        serviceBadge = findViewById(R.id.tvServiceBadge)
        accentPreview = findViewById(R.id.vAccentPreview)

        findViewById<View>(R.id.btnUseIME).setOnClickListener(this)
        findViewById<View>(R.id.btnSetIME).setOnClickListener(this)
        findViewById<View>(R.id.btnOpenA11y).setOnClickListener(this)
        findViewById<View>(R.id.btnStartService).setOnClickListener(this)
        findViewById<View>(R.id.btnSetDLNA).setOnClickListener(this)
        findViewById<View>(R.id.btnPickColor).setOnClickListener(this)

        findViewById<TextView>(R.id.tvVersion).text =
            "v" + AppPackagesHelper.getCurrentPackageVersion(this)
        dlnaNameText?.setText(DLNAUtils.getDLNANameSuffix(applicationContext))

        ControlCenter.init(this)
        initRootSwitch()
        requestStorageIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.btnUseIME -> {
                openInputMethodSettings()
            }
            R.id.btnSetIME -> {
                if (!Env.isEnableIME(this)) {
                    Env.toast(applicationContext, "请先激活启用输入法")
                    openInputMethodSettings()
                    return
                }
                try {
                    (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
                } catch (ignored: Exception) {
                    Env.toast(applicationContext, "无法打开输入法选择器")
                }
            }
            R.id.btnOpenA11y -> {
                try {
                    startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (ignored: Exception) {
                    Env.toast(applicationContext, "无法打开辅助功能设置")
                }
            }
            R.id.btnStartService -> {
                startService(Intent(IMEService.ACTION))
                if (!Env.isDefaultIME(this)) {
                    if (AdbHelper.getInstance() == null) AdbHelper.createInstance()
                }
                Env.toast(applicationContext, "服务已手动启动")
                refreshAll()
            }
            R.id.btnSetDLNA -> {
                dlnaNameText?.text?.toString()?.let {
                    DLNAUtils.setDLNANameSuffix(applicationContext, it)
                    Env.toast(applicationContext, "投屏名称已保存")
                }
            }
            R.id.btnPickColor -> showColorPicker()
        }
    }

    /** 全量刷新：二维码/功能状态/通道/主题预览 */
    private fun refreshAll() {
        refreshQRCode()
        refreshFeatureStates()
        refreshChannelSummary()
        refreshAccentPreview()
    }

    private fun refreshQRCode() {
        val address = RemoteServer.getServerAddress(this)
        addressView?.text = address
        qrCodeImage?.setImageBitmap(QRCodeGen.generateBitmap(address, 300, 300))
    }

    private fun refreshFeatureStates() {
        val imeEnabled = Env.isEnableIME(this)
        val imeDefault = Env.isDefaultIME(this)
        val a11yOn = MouseAccessibilityService.isServiceEnabled()

        findViewById<TextView>(R.id.tvImeState).text =
            if (imeEnabled) "✅ 输入法已启用" else "⬜ 输入法未启用"
        findViewById<TextView>(R.id.tvImeDefault).text =
            if (imeDefault) "✅ 已是系统默认输入法" else "⬜ 未设为默认输入法"
        findViewById<TextView>(R.id.tvA11yState).text =
            if (a11yOn) "✅ 辅助功能已开启（触控板可用）" else "⬜ 辅助功能未开启（触控板）"

        findViewById<TextView>(R.id.tvServiceState).text =
            if (imeDefault || a11yOn) "服务运行中" else "服务未运行"
        serviceBadge?.text = if (imeDefault || a11yOn) "运行中" else "未运行"
    }

    private fun refreshChannelSummary() {
        // 与 /channel-info 同源数据（04 文档：通道状态汇总行）
        val root = ControlCenter.root()
        val rootOn = root?.isAvailable() == true
        val imeOn = Env.isDefaultIME(this)
        val a11yOn = MouseAccessibilityService.isServiceEnabled()
        val adbOn = AdbHelper.getInstance()?.isRunning == true
        val sb = StringBuilder()
        sb.append("按键: ").append(when {
            rootOn -> "Root"
            imeOn -> "输入法"
            adbOn -> "ADB"
            else -> "-"
        })
        sb.append(" ｜ 文字: ").append(when {
            imeOn -> "输入法"
            rootOn -> "Root"
            adbOn -> "ADB"
            else -> "-"
        })
        sb.append(" ｜ 触控: ").append(when {
            a11yOn -> "辅助功能"
            rootOn -> "Root"
            adbOn -> "ADB"
            else -> "-"
        })
        findViewById<TextView>(R.id.tvChannelSummary).text = sb.toString()
    }

    private fun refreshAccentPreview() {
        val accent = ThemeHelper.currentAccent(this) ?: 0xFF6750A4.toInt()
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(accent)
            setStroke(2, Color.WHITE)
        }
        accentPreview?.background = bg
    }

    /** Root 开关（阶段 4 功能，就地展示探测结果） */
    private fun initRootSwitch() {
        val rootSwitch = findViewById<Switch>(R.id.swRoot)
        val rootState = findViewById<TextView>(R.id.tvRootState)
        val root = ControlCenter.root()

        rootSwitch.isChecked = RootPrefs.isEnabled(this)

        rootSwitch.setOnCheckedChangeListener { _, isChecked ->
            RootPrefs.setEnabled(this, isChecked)
            root?.invalidateCache()
            if (isChecked) {
                Thread {
                    val ok = root?.isAvailable() == true
                    runOnUiThread {
                        rootState.text = if (ok) "Root 增强：可用（su 已授权）" else "Root 增强：已开启但 su 不可用"
                        refreshChannelSummary()
                    }
                }.start()
            } else {
                rootState.text = "Root 增强：未开启"
                refreshChannelSummary()
            }
        }
        rootState.text = root?.describeStatus() ?: "Root 增强：未开启"
    }

    /** 主题色选择弹层（04 文档 5.1：10 预设 + 自定义 HEX） */
    private fun showColorPicker() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        // 预设色网格
        val grid = GridLayout(this).apply {
            columnCount = 5
        }
        ThemePrefs.presets.forEach { preset ->
            val swatch = View(this).apply {
                val size = 120
                layoutParams = GridLayout.LayoutParams().apply {
                    width = size
                    height = size
                    setMargins(12, 12, 12, 12)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(preset.hex)
                }
                tag = preset.id
                setOnClickListener { v ->
                    ThemePrefs.setAccent(this@MainActivity, v.tag as String)
                    applyThemeAndRecreate()
                }
            }
            grid.addView(swatch)
        }
        container.addView(grid)

        // "恢复默认" 按钮（跟随系统动态取色）
        val resetBtn = Button(this).apply {
            text = "恢复默认（跟随系统）"
            setOnClickListener {
                ThemePrefs.clearAccent(this@MainActivity)
                applyThemeAndRecreate()
            }
        }
        container.addView(resetBtn)

        // 自定义 HEX 输入
        val hexRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 24, 0, 0)
            gravity = Gravity.CENTER_VERTICAL
        }
        val hexInput = EditText(this).apply {
            hint = "#6750A4"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val hexBtn = Button(this).apply {
            text = "应用"
            setOnClickListener {
                val hex = hexInput.text.toString().trim()
                if (ThemePrefs.parseHex(hex) != null) {
                    ThemePrefs.setAccent(this@MainActivity, hex)
                    applyThemeAndRecreate()
                } else {
                    Env.toast(applicationContext, "色值无效，请输入 #RRGGBB 格式")
                }
            }
        }
        hexRow.addView(hexInput)
        hexRow.addView(hexBtn)
        container.addView(hexRow)

        AlertDialog.Builder(this)
            .setTitle("选择主题色")
            .setView(container)
            .setNegativeButton("关闭") { d, _ -> d.dismiss() }
            .show()
    }

    private fun applyThemeAndRecreate() {
        recreate()  // applyStyle 后重建立即生效（04 文档 5.2）
    }

    private fun openInputMethodSettings() {
        try {
            startActivityForResult(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS), 0)
        } catch (ignored: Exception) {
            Env.toast(applicationContext, "无法打开输入法设置")
        }
    }

    /** 阶段 2：存储权限引导（03 文档适配清单 2） */
    private fun requestStorageIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (!StorageHelper.hasFullDiskAccess(this)) {
                Env.toast(this, "建议授予「所有文件访问」权限，以使用完整的文件管理功能")
                StorageHelper.requestFullDiskAccess(this)
            }
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 100)
            }
        }
    }
}
