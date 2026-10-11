package com.tvremoteime.next

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tvremoteime.next.adb.AdbHelper
import com.tvremoteime.next.core.Env
import com.tvremoteime.next.IME.IMEService

/**
 * 开机/U盘挂载广播拉起服务（对应旧版 IMEServiceBroadCastReceiver.java）
 *
 * targetSdk 34+ 动态注册需 RECEIVER_EXPORTED flag；本类仅清单注册，
 * 由 AndroidManifest android:exported 显式声明（阶段 2 验证项 V5）。
 */
class IMEServiceBroadCastReceiver : BroadcastReceiver() {
    private val TAG = "IMEServiceBCR"

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "receive msg:" + intent.action)
        if (Intent.ACTION_BOOT_COMPLETED == intent.action || Intent.ACTION_MEDIA_MOUNTED == intent.action) {
            if (!Env.isDefaultIME(context)) {
                Log.d(TAG, "startService.....")
                context.startService(Intent(IMEService.ACTION))
                if (AdbHelper.getInstance() == null) AdbHelper.createInstance()
            }
        }
    }
}
