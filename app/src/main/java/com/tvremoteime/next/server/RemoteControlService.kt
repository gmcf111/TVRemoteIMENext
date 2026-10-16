package com.tvremoteime.next.server

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.tvremoteime.next.MainActivity
import com.tvremoteime.next.R
import com.tvremoteime.next.adb.AdbHelper
import com.tvremoteime.next.control.ControlCenter
import com.tvremoteime.next.control.ImeChannel
import com.tvremoteime.next.dlna.DLNAUtils
import java.io.IOException

/** HTTP/DLNA 的独立存活宿主。未选中本输入法时，Root/辅助功能/ADB 仍能工作。 */
class RemoteControlService : Service() {
    private var server: RemoteServer? = null

    override fun onCreate() {
        super.onCreate()
        com.tvremoteime.next.core.Env.initToastHandler()
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.remote_service_channel), NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        startForeground(9978, NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.remote_service_running))
            .setContentIntent(open).setOngoing(true).build())
        ControlCenter.init(this)
        ServerFileManager.resetBaseDir(this)
        AdbHelper.createInstance()
        AdbHelper.initService(this)
        for (port in 9978..9999) {
            val candidate = RemoteServer(port, applicationContext)
            candidate.dataReceiver = object : RemoteServer.DataReceiver {
                override fun onKeyEventReceived(keyCode: String, keyAction: Int): Boolean {
                    if (ImeChannel.dispatchKey(keyCode, keyAction)) return true
                    if (keyAction == 2) return true
                    if (keyCode.equals("cls", true)) return false
                    val code = android.view.KeyEvent.keyCodeFromString(keyCode)
                    return code != android.view.KeyEvent.KEYCODE_UNKNOWN && ControlCenter.sendKey(code)
                }

                override fun onTextReceived(text: String): Boolean = ControlCenter.sendText(text)
            }
            try {
                candidate.start()
                server = candidate
                RemoteServer.serverPort = port
                isRunning = true
                DLNAUtils.startDLNAService(applicationContext)
                return
            } catch (error: IOException) {
                candidate.stop()
                Log.w("RemoteControlService", "Port $port unavailable", error)
            }
        }
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        server?.stop()
        DLNAUtils.stopDLNAService()
        AdbHelper.stopService()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "remote_control"
        @Volatile var isRunning = false
            private set

        fun start(context: Context): Boolean = try {
            ContextCompat.startForegroundService(context, Intent(context, RemoteControlService::class.java))
            true
        } catch (error: Exception) {
            Log.e("RemoteControlService", "Unable to start service", error)
            false
        }
    }
}
