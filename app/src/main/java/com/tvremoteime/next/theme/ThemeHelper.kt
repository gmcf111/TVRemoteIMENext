package com.tvremoteime.next.theme

import android.app.Activity
import android.content.Context
import com.tvremoteime.next.R

/**
 * 主题应用助手（04 文档 5.2）
 *
 * - 预设色：XML overlay + applyStyle（正经资源系统路径）
 * - 自定义 HEX：TonalPalette 动态色阶，运行时注入强调位（主题系统无法承载运行时色，注入 View 层）
 * - 未选择：不叠加（API 31+ 走系统动态取色，低版本默认紫）
 */
object ThemeHelper {

    /** Activity.onCreate（setContentView 之前）调用 */
    fun applyTheme(activity: Activity) {
        val idOrHex = ThemePrefs.getAccentIdOrHex(activity) ?: return
        val overlayId = overlayResId(idOrHex)
        if (overlayId != 0) {
            activity.theme.applyStyle(overlayId, true)
        }
        // 自定义 HEX：无 overlay 可用，强调色经运行时注入（见 injectCustomAccent）
    }

    /** 预设 id → overlay 资源 ID；非预设返回 0 */
    fun overlayResId(idOrHex: String): Int = when (idOrHex) {
        "purple" -> R.style.ThemeOverlay_Next_Purple
        "blue" -> R.style.ThemeOverlay_Next_Blue
        "teal" -> R.style.ThemeOverlay_Next_Teal
        "green" -> R.style.ThemeOverlay_Next_Green
        "yellow" -> R.style.ThemeOverlay_Next_Yellow
        "orange" -> R.style.ThemeOverlay_Next_Orange
        "red" -> R.style.ThemeOverlay_Next_Red
        "pink" -> R.style.ThemeOverlay_Next_Pink
        "brown" -> R.style.ThemeOverlay_Next_Brown
        "skyblue" -> R.style.ThemeOverlay_Next_Skyblue
        else -> 0
    }

    /** 当前强调色（预设或自定义；未选择返回 null） */
    fun currentAccent(context: Context): Int? = ThemePrefs.getAccentColor(context)

    /** 自定义 HEX 时给关键强调 View 注入动态色（MainActivity 布局回调中调用） */
    fun injectCustomAccent(activity: Activity, vararg views: android.view.View?) {
        val hex = ThemePrefs.getAccentIdOrHex(activity) ?: return
        if (!hex.startsWith("#")) return
        val seed = ThemePrefs.parseHex(hex) ?: return
        val primary = com.tvremoteime.next.theme.TonalPalette.primary(seed)
        views.forEach { v ->
            v?.let {
                androidx.core.view.ViewCompat.setBackgroundTintList(it, android.content.res.ColorStateList.valueOf(primary))
            }
        }
    }

    /** /theme 端点数据（04 文档 5.4） */
    fun themeJson(context: Context): String {
        val idOrHex = ThemePrefs.getAccentIdOrHex(context)
        return if (idOrHex == null) {
            "{\"accentId\":null,\"primary\":null,\"custom\":false}"
        } else if (idOrHex.startsWith("#")) {
            val c = ThemePrefs.parseHex(idOrHex)
            "{\"accentId\":null,\"primary\":\"$idOrHex\",\"custom\":true}"
        } else {
            val preset = ThemePrefs.presetById(idOrHex)
            val hex = preset?.let { ThemePrefs.toHex(it.hex) } ?: "#6750A4"
            "{\"accentId\":\"$idOrHex\",\"primary\":\"$hex\",\"custom\":false}"
        }
    }
}
