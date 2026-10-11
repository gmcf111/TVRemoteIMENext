package com.tvremoteime.next.theme

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.graphics.drawable.GradientDrawable

/**
 * 键盘按键 drawable 动态工厂（04 文档 5.1：键盘跟随主题色，自绘层直接读持久化色值）
 *
 * 三种键型与 cacaview 的 key_modern/key_action/key_special_on/off 视觉对齐，
 * 颜色由当前主题色（ThemePrefs）派生：
 * - 字符键：白底圆角 + 按压浅色容器 + 主题色描边
 * - 功能键：主题色实底
 * - Shift 开/关：主题色实底 / 淡色容器
 *
 * API 21+ 走 RippleDrawable；4.4 用 StateListDrawable（无涟漪，视觉等价）。
 * IMEService 在 onCreateInputView 一次构建并缓存（零主题开销，≤300ms 红线不受影响）。
 */
object KeyboardDrawableFactory {

    private const val RADIUS = 8f

    /** 字符/数字键（key_modern 等价） */
    fun createModernKey(context: Context): android.graphics.drawable.Drawable {
        val accent = accent(context)
        val light = lighten(accent, 0.92f)
        val pressed = rounded(light, accentStroke = accent, strokeWidth = 1)
        val focused = rounded(light, accentStroke = accent, strokeWidth = 2)
        val normal = rounded(Color.WHITE, accentStroke = Color.parseColor("#E0E0E0"), strokeWidth = 1)
        return rippleOrSelector(context, accent, normal, pressed, focused)
    }

    /** 功能键：删除/回车/空格/网络/收起（key_action 等价，主题色实底） */
    fun createActionKey(context: Context): android.graphics.drawable.Drawable {
        val accent = accent(context)
        val pressed = rounded(darken(accent, 0.15f))
        val focused = rounded(darken(accent, 0.08f), accentStroke = Color.WHITE, strokeWidth = 2)
        val normal = rounded(accent)
        return rippleOrSelector(context, Color.WHITE, normal, pressed, focused)
    }

    /** Shift 开（key_special_on 等价：主题色实底） */
    fun createShiftOnKey(context: Context): android.graphics.drawable.Drawable {
        val accent = accent(context)
        val pressed = rounded(darken(accent, 0.15f))
        val normal = rounded(accent)
        return rippleOrSelector(context, accent, normal, pressed, null)
    }

    /** Shift 关（key_special_off 等价：淡色容器 + 描边） */
    fun createShiftOffKey(context: Context): android.graphics.drawable.Drawable {
        val accent = accent(context)
        val container = lighten(accent, 0.88f)
        val pressed = rounded(lighten(accent, 0.75f), accentStroke = accent, strokeWidth = 1)
        val normal = rounded(container, accentStroke = lighten(accent, 0.5f), strokeWidth = 1)
        return rippleOrSelector(context, accent, normal, pressed, null)
    }

    // ============ 内部 ============

    private fun accent(context: Context): Int =
        ThemePrefs.getAccentColor(context) ?: 0xFF0061A4.toInt()

    private fun rounded(fill: Int, accentStroke: Int? = null, strokeWidth: Int = 1): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = RADIUS
            accentStroke?.let { setStroke(strokeWidth, it) }
        }

    private fun rippleOrSelector(
        context: Context,
        rippleColor: Int,
        normal: android.graphics.drawable.Drawable,
        pressed: android.graphics.drawable.Drawable,
        focused: android.graphics.drawable.Drawable?
    ): android.graphics.drawable.Drawable {
        val selector = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            focused?.let { addState(intArrayOf(android.R.attr.state_focused), it) }
            addState(intArrayOf(), normal)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return selector
        // API 21+：涟漪
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.WHITE)
            cornerRadius = RADIUS
        }
        return RippleDrawable(ColorStateList.valueOf(rippleColor), selector, mask)
    }

    /** 向白混合（ratio 0~1，越大越浅） */
    private fun lighten(color: Int, ratio: Float): Int {
        val r = Color.red(color) + ((255 - Color.red(color)) * ratio).toInt()
        val g = Color.green(color) + ((255 - Color.green(color)) * ratio).toInt()
        val b = Color.blue(color) + ((255 - Color.blue(color)) * ratio).toInt()
        return Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }

    /** 向黑混合（ratio 0~1，越大越深） */
    private fun darken(color: Int, ratio: Float): Int {
        val r = (Color.red(color) * (1 - ratio)).toInt()
        val g = (Color.green(color) * (1 - ratio)).toInt()
        val b = (Color.blue(color) * (1 - ratio)).toInt()
        return Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }
}
