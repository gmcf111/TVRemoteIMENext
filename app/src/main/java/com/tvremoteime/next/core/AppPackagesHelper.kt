package com.tvremoteime.next.core

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.Serializable

/**
 * 应用包管理工具（对应旧版 AppPackagesHelper.java）
 */
object AppPackagesHelper {
    private const val TAG = "AppPackagesHelper"

    class AppInfo : Serializable {
        var label: String = ""
        var packageName: String = ""
        var apkPath: String = ""
        var isSysApp: Boolean = false

        fun toJSONObject(): JSONObject = JSONObject().apply {
            try {
                put("lable", label)
                put("packageName", packageName)
                put("apkPath", apkPath)
                put("isSysApp", isSysApp)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getCurrentPackageVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
    } catch (e: PackageManager.NameNotFoundException) {
        "1.0.0"
    }

    fun queryAppInfo(context: Context, containSysApp: Boolean): List<AppInfo> {
        val pm = context.packageManager
        val appInfos = mutableListOf<AppInfo>()
        // API 30+ 需 <queries> 声明；GET_UNINSTALLED_PACKAGES 沿旧语义
        val listAppcations = pm.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)
        for (app in listAppcations) {
            val isSysApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (containSysApp || !isSysApp) {
                // 过滤掉系统底层的 app
                if (isSysApp && (app.packageName.startsWith("com.android.") || app.packageName == "android")) continue
                val appInfo = AppInfo()
                appInfo.label = app.loadLabel(pm).toString()
                appInfo.packageName = app.packageName
                appInfo.apkPath = app.sourceDir
                appInfo.isSysApp = isSysApp
                appInfos.add(appInfo)
            }
        }
        return appInfos.sortedWith(compareBy({ if (it.isSysApp) 2 else 1 }, { it.label }))
    }

    fun getQueryAppInfoJsonString(context: Context, containSysApp: Boolean): String {
        val array = JSONArray()
        for (app in queryAppInfo(context, containSysApp)) array.put(app.toJSONObject())
        return array.toString()
    }

    private fun getApplicationInfo(packageName: String, context: Context): ApplicationInfo? {
        if (packageName.isEmpty()) return null
        return try {
            context.packageManager.getApplicationInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    fun installPackage(apkFile: File, context: Context) {
        try {
            // API 24+ FileUriExposedException 防护：走 FileProvider
            val uri: Uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                androidx.core.content.FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", apkFile
                )
            } else {
                Uri.fromFile(apkFile)
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setDataAndType(uri, "application/vnd.android.package-archive")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            context.startActivity(intent)
            Log.i(TAG, "已安装应用包[${apkFile.name}]")
        } catch (e: Exception) {
            Log.e(TAG, "安装应用包[${apkFile.name}]出错", e)
        }
    }

    fun uninstallPackage(packageName: String, context: Context) {
        if (getApplicationInfo(packageName, context) == null) return
        try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                data = Uri.parse("package:$packageName")
            }
            context.startActivity(intent)
            Log.i(TAG, "已删除应用包[$packageName]")
        } catch (e: Exception) {
            Log.e(TAG, "删除应用包[$packageName]出错", e)
        }
    }

    fun runPackage(packageName: String, context: Context) {
        if (getApplicationInfo(packageName, context) == null) return
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            intent?.let { context.startActivity(it) }
            Log.i(TAG, "已运行应用包[$packageName]")
        } catch (e: Exception) {
            Log.e(TAG, "运行应用包[$packageName]出错", e)
        }
    }

    fun runSystemPackage(packageName: String, context: Context) {
        try {
            val intent = Intent(packageName).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
            Log.i(TAG, "已运行系统应用包[$packageName]")
        } catch (e: Exception) {
            Log.e(TAG, "运行系统应用包[$packageName]出错", e)
        }
    }

    fun getAppIcon(packageName: String, context: Context): ByteArray? {
        val applicationInfo = getApplicationInfo(packageName, context) ?: return null
        val bitmap = (applicationInfo.loadIcon(context.packageManager) as? BitmapDrawable)?.bitmap ?: return null
        val data = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, data)
        return data.toByteArray()
    }
}
