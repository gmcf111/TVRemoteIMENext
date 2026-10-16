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
    private var boundContext: Context? = null
    private var connection: ServiceConnection? = null
    private var upnp: AndroidUpnpService? = null

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
        if (connection != null) {
            upnp?.let { refreshRenderer(context.applicationContext, it) }
            return
        }
        val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                if (connection !== this) return
                val upnpService = service as? AndroidUpnpService ?: return
                upnp = upnpService
                Log.i(TAG, "DLNA: onServiceConnected")
                refreshRenderer(context.applicationContext, upnpService)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                upnp = null
                Log.i(TAG, "DLNA: onServiceDisconnected")
            }
        }
        connection = serviceConnection
        boundContext = context.applicationContext
        if (!context.applicationContext.bindService(Intent(context, AndroidUpnpServiceImpl::class.java), serviceConnection, Context.BIND_AUTO_CREATE)) {
            connection = null
            boundContext = null
        }
    }

    private fun refreshRenderer(context: Context, service: AndroidUpnpService) {
        val suffix = getDLNANameSuffix(context)
        val name = context.getString(R.string.app_name) + if (suffix.isEmpty()) "" else "($suffix)"
        mMediaRenderer?.let { it.stopAllMediaPlayers(); service.registry.removeDevice(it.device) }
        mMediaRenderer = ZxtMediaRenderer(1, name, context)
        service.registry.addDevice(mMediaRenderer!!.device)
    }

    fun stopDLNAService() {
        mMediaRenderer?.stopAllMediaPlayers()
        mMediaRenderer?.let { upnp?.registry?.removeDevice(it.device) }
        connection?.let { boundContext?.unbindService(it) }
        mMediaRenderer = null
        upnp = null
        connection = null
        boundContext = null
    }
}
