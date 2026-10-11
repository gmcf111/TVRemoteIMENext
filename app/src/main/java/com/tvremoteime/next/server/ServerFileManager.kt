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
 * baseDir 阶段 1 保持旧版语义（外部存储根）；
 * 阶段 2 切换为 MANAGE_EXTERNAL_STORAGE 引导 + 降级（02/03 文档）。
 */
object ServerFileManager {
    private const val TAG = "ServerFileManager"

    @Volatile
    var baseDir: File = android.os.Environment.getExternalStorageDirectory()
        private set

    private var filesDir = File(baseDir, "tvremoteime/files")
    private var tmpDataDir = File(baseDir, "tvremoteime/temp")
    private var playerCacheDir = File(baseDir, "tvremoteime/xlplayer")

    fun resetBaseDir(context: Context) {
        // 阶段 1：保持与旧版一致的全盘路径（4.4-10 系统直接可写）
        baseDir = android.os.Environment.getExternalStorageDirectory()
        filesDir = File(baseDir, "tvremoteime/files")
        tmpDataDir = File(baseDir, "tvremoteime/temp")
        playerCacheDir = File(baseDir, "tvremoteime/xlplayer")
    }

    fun getPlayTorrentFile(): File = File(baseDir, "tvremoteime/play.torrent")

    fun getScreenShotFile(): File = File(baseDir, "tvremoteime/screenshot.png")

    private class SDCardTempFile(fileName: String?) : NanoHTTPD.TempFile {
        private val file: File
        private val isTemp: Boolean
        private var fstream: OutputStream? = null

        init {
            if (fileName.isNullOrEmpty()) {
                file = File.createTempFile("tmp-", "", tmpDataDir)
                isTemp = true
            } else {
                file = File(filesDir, fileName)
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
                name = URLDecoder.decode(name, "utf-8")
                    .replace(Regex("[\\\\|/]"), "")
                    .replace("..", "")
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
            if (playerCacheDir.exists()) deleteDirFiles(playerCacheDir)
            val torrentFile = getPlayTorrentFile()
            if (torrentFile.exists()) torrentFile.delete()
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
