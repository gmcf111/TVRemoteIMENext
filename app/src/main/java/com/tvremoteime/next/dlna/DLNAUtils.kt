package com.tvremoteime.next.dlna

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.IBinder
import android.text.TextUtils
import android.util.Log
import com.tvremoteime.next.R
import com.tvremoteime.next.core.Env
import com.zxt.dlna.dmr.ZxtMediaRenderer
import org.fourthline.cling.android.AndroidUpnpService
import org.fourthline.cling.android.AndroidUpnpServiceImpl

/**
 * DLNA 投屏工具（对应旧版 DLNAUtils.java）
 *
 * 阶段 1 暂沿用 DroidDLNA 旧实现（ZxtMediaRenderer + Cling 2.1）；
 * 阶段 3 后按 03 文档切换 jUPnP/Cling Maven 依赖 + Kotlin 业务重封装。
 */
object DLNAUtils {
    private const val TAG = "DLNAUtils"
    private var mMediaRenderer: ZxtMediaRenderer? = null

    fun setDLNANameSuffix(context: Context, nameSuffix: String) {
        val sharedPreferences = context.applicationContext.getSharedPreferences("dlna_settings", 0)
        sharedPreferences.edit().putString("nameSuffix", nameSuffix).commit()
        startDLNAService(context)
    }

    fun getDLNANameSuffix(context: Context): String {
        val sharedPreferences = context.applicationContext.getSharedPreferences("dlna_settings", 0)
        return sharedPreferences.getString("nameSuffix", "") ?: ""
    }

    fun startDLNAService(context: Context) {
        val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val upnpService = service as? AndroidUpnpService ?: return
                Log.i(TAG, "DLNA: onServiceConnected")
                val appContext = context.applicationContext
                var dlnaName = appContext.getString(R.string.app_name)
                val dlnaNameSuffix = getDLNANameSuffix(appContext)
                if (!TextUtils.isEmpty(dlnaNameSuffix)) dlnaName += "($dlnaNameSuffix)"
                if (mMediaRenderer != null) {
                    mMediaRenderer?.stopAllMediaPlayers()
                    upnpService.registry.removeAllLocalDevices()
                    mMediaRenderer = null
                }
                mMediaRenderer = ZxtMediaRenderer(1, dlnaName, appContext)
                upnpService.registry.addDevice(mMediaRenderer!!.device)
                Env.toastInHandler(appContext, appContext.getString(R.string.app_name) + " DLNA服务已启动")
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                Log.i(TAG, "DLNA: onServiceDisconnected")
            }
        }
        context.bindService(
            Intent(context, AndroidUpnpServiceImpl::class.java),
            serviceConnection, Context.BIND_AUTO_CREATE
        )
    }

    fun stopDLNAService() {
        mMediaRenderer?.stopAllMediaPlayers()
    }
}
