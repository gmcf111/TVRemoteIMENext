package com.tvremoteime.next.control

import java.io.ByteArrayOutputStream

object PngScreenshot {
    private val signature = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
    private val translatedSignature = byteArrayOf(-119, 80, 78, 71, 13, 13, 10, 26, 13, 10)

    /** PNG 本身就含 CRLF。仅在魔数证明发生了换行转换时，逆转一次 CRLF 污染。 */
    fun normalize(data: ByteArray): ByteArray? {
        fun startsWith(prefix: ByteArray) = data.size >= prefix.size &&
            prefix.indices.all { data[it] == prefix[it] }
        if (startsWith(signature)) return data
        if (!startsWith(translatedSignature)) return null
        val output = ByteArrayOutputStream(data.size)
        var i = 0
        while (i < data.size) {
            if (data[i] == 13.toByte() && i + 1 < data.size && data[i + 1] == 10.toByte()) i++
            output.write(data[i++].toInt())
        }
        return output.toByteArray()
    }
}
