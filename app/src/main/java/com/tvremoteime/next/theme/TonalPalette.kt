package com.tvremoteime.next.theme

import android.graphics.Color

/**
 * Tonal Palette 动态生成（04 文档 5.2：内置算法 ~100 行，不落 XML）
 *
 * 由单一种子色生成 MD3 tonal 色阶（tone 0-100）。
 * 采用 HCT 近似：HSL 空间保持色相与饱和度、按目标亮度映射，
 * 对盒子场景足够（非像素级 Material You 复刻）。
 */
object TonalPalette {

    /** 生成 tone 值颜色（tone 0=黑 100=白，MD3 惯用 40/90/10/30/80 档位） */
    fun toneOf(seed: Int, tone: Int): Int {
        val t = tone.coerceIn(0, 100)
        val hsl = seedToHsl(seed)
        // 目标亮度线性映射：tone -> [0,1]
        val targetL = t / 100f
        // 高 tone 降饱和、低 tone 降饱和（模拟 MD3 tonal 视觉）
        var sat = hsl[1]
        sat = when {
            t >= 90 -> sat * 0.45f
            t >= 80 -> sat * 0.62f
            t >= 70 -> sat * 0.78f
            t <= 10 -> sat * 0.30f
            t <= 20 -> sat * 0.55f
            else -> sat
        }
        val out = FloatArray(3)
        out[0] = hsl[0]
        out[1] = sat.coerceIn(0f, 1f)
        out[2] = adjustLightness(targetL, t)
        return Color.HSVToColor(out)
    }

    /** 主色（tone 40） */
    fun primary(seed: Int): Int = toneOf(seed, 40)

    /** 主色容器（tone 90 亮 / tone 30 暗） */
    fun primaryContainer(seed: Int, dark: Boolean): Int =
        if (dark) toneOf(seed, 30) else toneOf(seed, 90)

    fun onPrimaryContainer(seed: Int, dark: Boolean): Int =
        if (dark) toneOf(seed, 90) else toneOf(seed, 10)

    fun onPrimary(seed: Int): Int = toneOf(seed, 100)

    /** 次级容器 */
    fun secondaryContainer(seed: Int, dark: Boolean): Int =
        if (dark) toneOf(seed, 25) else toneOf(seed, 92)

    private fun seedToHsl(seed: Int): FloatArray {
        val hsv = FloatArray(3)
        Color.colorToHSV(seed, hsv)
        return hsv
    }

    /** tone -> HSV 亮度映射（两端收敛，中段近似线性） */
    private fun adjustLightness(targetL: Float, tone: Int): Float {
        // HSV 的 V 与 MD3 tone 方向相反：tone 高 = 亮 = V 大
        // 直接采用 targetL 作为 V（本实现色彩空间近似，视觉可用）
        return targetL.coerceIn(0.02f, 0.98f)
    }

    /**
     * 计算对比度（Web 端与自检用）
     * 返回 [fg, bg] 的 WCAG 相对亮度对比度
     */
    fun contrast(fg: Int, bg: Int): Double {
        val l1 = relativeLuminance(fg)
        val l2 = relativeLuminance(bg)
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Int): Double {
        fun channel(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(Color.red(color)) +
                0.7152 * channel(Color.green(color)) +
                0.0722 * channel(Color.blue(color))
    }
}
