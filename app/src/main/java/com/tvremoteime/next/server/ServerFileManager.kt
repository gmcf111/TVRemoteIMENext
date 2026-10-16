package com.tvremoteime.next.server

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.URLDecoder

/**
 * 服务器文件管理（对应旧版 RemoteServerFileManager.java）
 *
 * 两个目录语义（与旧版一致，拆分显式化）：
 * - dataDir：应用外部私有目录（getExternalFilesDir）——上传落地、缓存
 * - storageRoot：文件浏览/下载的根——全盘授权=外部存储根（旧版体验）；未授权=私有目录降级
 *
 * v3.0：移除播放器下载缓存目录（xlplayer）与 play.torrent。
 */
object ServerFileManager {
    private const val TAG = "ServerFileManager"

    /** 应用私有数据目录（上传文件、缓存） */
    @Volatile
    var dataDir: File = File(android.os.Environment.getExternalStorageDirectory(), "Android/data/com.tvremoteime.next/files")
        private set

    /** 文件浏览根（Web 端文件管理） */
    @Volatile
    var storageRoot: File = android.os.Environment.getExternalStorageDirectory()
        private set

    private var filesDir = File(dataDir, "files")
    private var tmpDataDir = File(dataDir, "temp")

    fun resetBaseDir(context: Context) {
        dataDir = context.getExternalFilesDir(null) ?: context.filesDir
        storageRoot = com.tvremoteime.next.core.StorageHelper.getStorageRoot(context)
        filesDir = File(dataDir, "files")
        tmpDataDir = File(dataDir, "temp")
        try {
            if (!filesDir.exists()) filesDir.mkdirs()
            if (!tmpDataDir.exists()) tmpDataDir.mkdirs()
        } catch (ignored: Exception) {
        }
    }

    fun refreshStorageRoot(context: Context) {
        storageRoot = com.tvremoteime.next.core.StorageHelper.getStorageRoot(context)
    }

    fun getScreenShotFile(): File = File(dataDir, "screenshot.png")

    private class SDCardTempFile(fileName: String?) : NanoHTTPD.TempFile {
        private val file: File
        private val isTemp: Boolean
        private var fstream: OutputStream? = null

        init {
            if (fileName.isNullOrEmpty()) {
                file = File.createTempFile("tmp-", "", tmpDataDir)
                isTemp = true
            } else {
                // 每个上传拥有独立目录，避免同名并发请求相互截断文件。
                val uploadDir = File(filesDir, java.util.UUID.randomUUID().toString())
                if (!uploadDir.mkdirs()) throw IOException("Cannot create upload directory")
                file = File(uploadDir, fileName)
                isTemp = false
            }
            try {
                if (file.exists()) file.delete()
            } catch (ignored: Exception) {
            }
        }

        @Throws(Exception::class)
        override fun delete() {
            fstream?.close()
            // 上传的目标文件不删除，仅删除临时文件
            if (isTemp && !file.delete()) {
                Log.e(TAG, "无法删除临时文件[${name}]")
            }
        }

        override fun getName(): String = file.absolutePath

        @Throws(Exception::class)
        override fun open(): OutputStream {
            if (fstream == null) fstream = FileOutputStream(file)
            return fstream!!
        }
    }

    class Factory : NanoHTTPD.TempFileManagerFactory {
        override fun create(): NanoHTTPD.TempFileManager = ServerTempFileManager()
    }

    private class ServerTempFileManager : NanoHTTPD.TempFileManager {
        private val tempFiles = mutableListOf<NanoHTTPD.TempFile>()

        override fun clear() {
            for (file in tempFiles) {
                try {
                    file.delete()
                } catch (e: Exception) {
                    Log.e(TAG, "删除临时文件[${file.name}]时出错.", e)
                }
            }
            tempFiles.clear()
        }

        @Throws(Exception::class)
        override fun createTempFile(fileName: String?): NanoHTTPD.TempFile {
            var name = fileName
            if (!name.isNullOrEmpty()) {
                name = (try { URLDecoder.decode(name.replace("+", "%2B"), "utf-8") } catch (_: IllegalArgumentException) { name })
                    .replace(Regex("[\\\\/]"), "")
                    .replace("..", "")
                require(name.isNotBlank() && name != "." && '\u0000' !in name) { "Invalid upload filename" }
            }
            val tmpFile = SDCardTempFile(name)
            tempFiles.add(tmpFile)
            return tmpFile
        }
    }

    fun clearAllFiles() {
        try {
            if (filesDir.exists()) deleteDirFiles(filesDir)
            if (tmpDataDir.exists()) deleteDirFiles(tmpDataDir)
        } catch (ignored: Exception) {
        }
    }

    fun deleteDirFiles(file: File) {
        val files = file.listFiles() ?: return
        for (f in files) {
            try {
                if (f.isDirectory) deleteDirFiles(f)
                f.delete()
            } catch (ignored: SecurityException) {
            }
        }
    }

    fun deleteFile(file: File) {
        if (file.exists()) {
            if (file.isDirectory) deleteDirFiles(file)
            try {
                file.delete()
            } catch (ignored: SecurityException) {
            }
        }
    }

    fun cutFile(sourceFile: File, targetPath: File) {
        if (sourceFile.exists()) {
            val targetFile = File(targetPath, sourceFile.name)
            try {
                sourceFile.renameTo(targetFile)
            } catch (ignored: SecurityException) {
            }
        }
    }

    fun copyFile(sourceFile: File, targetPath: File) {
        if (!sourceFile.exists()) return
        if (sourceFile.isDirectory) {
            val newDir = File(targetPath, sourceFile.name)
            if (newDir.mkdir()) {
                sourceFile.listFiles()?.forEach { copyFile(it, newDir) }
            }
        } else {
            val targetFile = File(targetPath, sourceFile.name)
            copyFileImpl(sourceFile, targetFile)
        }
    }

    private fun copyFileImpl(sourceFile: File, targetFile: File) {
        if (targetFile.exists()) return
        try {
            sourceFile.inputStream().use { input ->
                targetFile.outputStream().use { output -> input.copyTo(output, 8192) }
            }
        } catch (ignored: Exception) {
        }
    }
}
