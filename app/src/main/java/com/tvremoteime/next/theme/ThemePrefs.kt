package com.tvremoteime.next.theme

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

/**
 * 主题色持久化（04 文档 5.2）
 *
 * SharedPreferences("next_theme")：accent_color（预设 id 或 hex）、custom_palette
 */
object ThemePrefs {
    private const val PREFS = "next_theme"
    private const val KEY_ACCENT = "accent_color"

    /** 预设色板（04 文档 5.3，10 色） */
    data class Preset(val id: String, val nameZh: String, val hex: Int)

    val presets: List<Preset> = listOf(
        Preset("purple", "Next 紫", 0xFF6750A4.toInt()),
        Preset("blue", "蓝", 0xFF1565C0.toInt()),
        Preset("teal", "青", 0xFF00897B.toInt()),
        Preset("green", "绿", 0xFF2E7D32.toInt()),
        Preset("yellow", "黄", 0xFFF9A825.toInt()),
        Preset("orange", "橙", 0xFFEF6C00.toInt()),
        Preset("red", "红", 0xFFC62828.toInt()),
        Preset("pink", "粉", 0xFFAD1457.toInt()),
        Preset("brown", "茶灰", 0xFF5D4037.toInt()),
        Preset("skyblue", "天蓝", 0xFF0061A4.toInt())
    )

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 当前主题色（Int ARGB）；null = 未选择（跟随系统动态取色/默认紫） */
    fun getAccentColor(context: Context): Int? {
        val v = prefs(context).getString(KEY_ACCENT, null) ?: return null
        return parseHex(v) ?: presetById(v)?.hex
    }

    /** 预设 id 或 "#RRGGBB"；null = 未选择 */
    fun getAccentIdOrHex(context: Context): String? =
        prefs(context).getString(KEY_ACCENT, null)

    fun isCustom(context: Context): Boolean {
        val v = prefs(context).getString(KEY_ACCENT, null) ?: return false
        return v.startsWith("#")
    }

    fun setAccent(context: Context, idOrHex: String) {
        prefs(context).edit().putString(KEY_ACCENT, idOrHex).commit()
    }

    fun clearAccent(context: Context) {
        prefs(context).edit().remove(KEY_ACCENT).commit()
    }

    fun parseHex(v: String): Int? = try {
        when {
            v.length == 7 && v.startsWith("#") -> Color.parseColor(v)
            v.length == 4 && v.startsWith("#") -> Color.parseColor("#" + v.drop(1).flatMap { listOf(it, it) }.joinToString(""))
            else -> null
        }
    } catch (e: Exception) {
        null
    }

    fun presetById(id: String): Preset? = presets.firstOrNull { it.id == id }

    fun toHex(color: Int): String = String.format("#%06X", 0xFFFFFF and color)
}
