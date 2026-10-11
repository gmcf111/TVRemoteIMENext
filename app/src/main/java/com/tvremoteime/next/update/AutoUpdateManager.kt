package com.tvremoteime.next.update

import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.util.Log
import com.tvremoteime.next.R
import com.tvremoteime.next.core.AppPackagesHelper
import com.tvremoteime.next.http.HttpGet
import org.json.JSONObject
import java.io.File

/**
 * 自更新管理（对应旧版 AutoUpdateManager.java）
 *
 * 更新源（用户决策 D7）：本项目 GitHub Releases 的 version.json。
 * 字段沿用旧版格式：versionCode / versionName / installAPK / message / forced。
 */
class AutoUpdateManager(
    private val context: Context,
    private val handler: Handler
) {
    companion object {
        private const val TAG = "AutoUpdateManager"
        // 可配置：换仓库/镜像只需改这一处
        private const val VERSION_URL =
            "https://github.com/gmcf111/TVRemoteIMENext/releases/latest/download/version.json"
    }

    private val localFile: File = File(context.externalCacheDir, context.getString(R.string.app_name) + ".apk")

    init {
        startUpdateThread()
    }

    private fun startUpdateThread() {
        Thread {
            try {
                val versionObj = getServerVersionObj()
                if (versionObj != null && needUpdate(versionObj)) {
                    if (downloadInstallAPK(versionObj)) {
                        val message = if (versionObj.has("message")) versionObj.getString("message") else ""
                        val versionName =
                            if (versionObj.has("versionName")) versionObj.getString("versionName")
                            else AppPackagesHelper.getCurrentPackageVersion(context)
                        val msg = StringBuilder().append("发现新版本：").append(versionName)
                        if (message.isNotEmpty()) {
                            msg.append("\r\n更新内容：\r\n\r\n").append(message)
                        }
                        val content = msg.toString()
                        val forced = versionObj.has("forced") && versionObj.getBoolean("forced")

                        handler.post {
                            try {
                                val builder = AlertDialog.Builder(context)
                                builder.setTitle(context.getString(R.string.app_name) + "版本更新提示")
                                builder.setIcon(R.drawable.ic_launcher)
                                builder.setMessage(content)
                                if (!forced) {
                                    builder.setPositiveButton("稍后更新") { dialog, _ -> dialog.dismiss() }
                                }
                                builder.setNegativeButton("马上更新") { dialog, _ ->
                                    AppPackagesHelper.installPackage(localFile, context)
                                    dialog.dismiss()
                                }
                                val dialog = builder.create()
                                dialog.setCanceledOnTouchOutside(false)
                                dialog.show()
                            } catch (ex: Exception) {
                                // IME 上下文无窗口令牌时直接拉起安装
                                AppPackagesHelper.installPackage(localFile, context)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "startUpdateThread", e)
            }
        }.start()
    }

    private fun getCurrentPackageVersion(): Int = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionCode
    } catch (e: PackageManager.NameNotFoundException) {
        -1
    }

    private fun getServerVersionObj(): JSONObject? {
        return try {
            val jsonData = HttpGet.readString(VERSION_URL)
            if (!jsonData.isNullOrEmpty()) JSONObject(jsonData) else null
        } catch (e: Exception) {
            Log.e(TAG, "getServerVersionObj", e)
            null
        }
    }

    private fun needUpdate(versionObj: JSONObject): Boolean {
        val version = getCurrentPackageVersion()
        if (version == -1) return false
        return try {
            versionObj.has("versionCode") && versionObj.has("installAPK") &&
                    version < versionObj.getInt("versionCode")
        } catch (e: Exception) {
            false
        }
    }

    private fun needDownloadAPK(versionObj: JSONObject): Boolean {
        return try {
            if (!localFile.exists()) return true
            val packInfo = context.packageManager.getPackageArchiveInfo(
                localFile.absolutePath, PackageManager.GET_ACTIVITIES
            ) ?: return true
            packInfo.versionCode < versionObj.getInt("versionCode")
        } catch (e: Exception) {
            true
        }
    }

    private fun downloadInstallAPK(versionObj: JSONObject): Boolean {
        return try {
            if (!needDownloadAPK(versionObj)) return true
            val url = versionObj.getString("installAPK")
            if (url.isNullOrEmpty()) return false
            HttpGet.downloadFile(url, localFile)
        } catch (e: Exception) {
            Log.e(TAG, "downloadInstallAPK", e)
            false
        }
    }
}
