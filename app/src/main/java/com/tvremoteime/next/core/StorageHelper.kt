package com.tvremoteime.next.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import java.io.File

/**
 * 存储访问抽象（阶段 2 新增，02/03 文档）
 *
 * - API 29-：WRITE_EXTERNAL_STORAGE 运行时权限即可全盘读写（旧版行为）
 * - API 30+：MANAGE_EXTERNAL_STORAGE"所有文件访问"授权后全盘；
 *   未授权时降级 getExternalFilesDir()（Web 端只能看到应用私有目录，
 *   /sdcard_stat 等仍可用，MainActivity/IME 引导用户授权）
 *
 * 盒子类应用维持旧版全盘文件管理体验（03 文档决策：走 AllFilesAccess 路径）。
 */
object StorageHelper {

    /** 是否已具备全盘读写能力 */
    fun hasFullDiskAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            // API 23-29：写权限即可全盘写；API 19-22 install time 权限
            true
        }
    }

    /** 当前可用的存储根：全盘授权 → /storage/emulated/0；否则 → 应用外部私有目录 */
    fun getStorageRoot(context: Context): File {
        return if (hasFullDiskAccess(context)) {
            Environment.getExternalStorageDirectory()
        } else {
            context.getExternalFilesDir(null)
                ?: Environment.getExternalStorageDirectory()
        }
    }

    /** 跳系统"所有文件访问"设置页（仅 API 30+ 有意义） */
    fun requestFullDiskAccess(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.fromParts("package", context.packageName, null)
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) {
                // 无此 Activity 的 ROM（部分国产盒子）兜底到应用详情页
                try {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (ignored: Exception) {
                }
            }
        }
    }

    /**
     * 路径可见性说明（Web 端展示用）。
     * 返回当前根目录：全盘授权为 ""（相对 /storage/emulated/0），降级为应用私有目录。
     */
    fun describeRoot(context: Context): String {
        val root = getStorageRoot(context)
        return if (hasFullDiskAccess(context)) {
            "已授权全盘访问：${root.absolutePath}"
        } else {
            "未授权全盘访问（降级模式，仅可访问应用私有目录）：${root.absolutePath}"
        }
    }
}
