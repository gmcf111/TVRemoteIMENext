package com.tvremoteime.next.core

import java.io.File
import java.io.IOException

/** 路径已经由 HTTP 层解码；这里不能再次 URLDecode（会破坏 +、% 和中文文件名）。 */
object FileAccess {
    fun resolve(root: File, path: String?): File? {
        if (path == null || '\u0000' in path) return null
        val relative = path.replace('\\', '/').trimStart('/')
        if (relative.split('/').any { it == ".." }) return null
        return try {
            val base = root.canonicalFile
            val file = File(base, relative).canonicalFile
            file.takeIf { it == base || it.path.startsWith(base.path + File.separator) }
        } catch (_: IOException) {
            null
        }
    }

    /** 拒绝复制到自身/子目录；失败时抛出异常，避免向客户端报告虚假的成功。 */
    fun copy(source: File, directory: File, move: Boolean = false) {
        val src = source.canonicalFile
        val target = File(directory, source.name).canonicalFile
        require(src.exists() && directory.isDirectory) { "Source or destination does not exist" }
        require(target != src && !target.path.startsWith(src.path + File.separator)) {
            "Cannot copy a file into itself or its descendants"
        }
        require(!target.exists()) { "Destination already exists" }
        if (move && src.renameTo(target)) return
        try {
            src.copyRecursively(target, overwrite = false, onError = { _, error -> throw error })
        } catch (error: Exception) {
            target.deleteRecursively()
            throw error
        }
        if (move && !src.deleteRecursively()) throw IOException("Cannot remove source after copying")
    }
}
