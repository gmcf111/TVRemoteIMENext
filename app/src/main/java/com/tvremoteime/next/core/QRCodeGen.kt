package com.tvremoteime.next.core

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * 二维码生成（对应旧版 QRCodeGen.java，ZXing Maven 依赖替代本地 jar）
 */
object QRCodeGen {
    fun generateBitmap(content: String, width: Int, height: Int): Bitmap? = try {
        val hints = mapOf(EncodeHintType.CHARACTER_SET to "utf-8")
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, width, height, hints)
        val pixels = IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            if (matrix.get(x, y)) Color.TRANSPARENT else Color.WHITE
        }
        Bitmap.createBitmap(pixels, 0, width, width, height, Bitmap.Config.RGB_565)
    } catch (e: Exception) {
        null
    }
}
