package com.example.flashlight

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class TorchService : Service() {

    companion object {
        const val ACTION_TOGGLE = "com.example.flashlight.TOGGLE"
        const val ACTION_REFRESH = "com.example.flashlight.REFRESH"
        private const val CHANNEL_ID = "torch"
        private const val NOTIF_ID = 1
    }

    private lateinit var cm: CameraManager
    private var camId: String? = null
    private var torchOn = false
    private var onWhenScreenWentOff = false

    // Keeps our state in sync if the torch is changed by something else.
    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == camId) {
                torchOn = enabled
                refreshNotification()
            }
        }
    }

    // Power button with screen off -> screen turns on -> we switch the torch off.
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> onWhenScreenWentOff = torchOn
                Intent.ACTION_SCREEN_ON -> {
                    if (onWhenScreenWentOff && torchOn) setTorch(false)
                    onWhenScreenWentOff = false
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        camId = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }

        val channel = NotificationChannel(CHANNEL_ID, "Flashlight", NotificationManager.IMPORTANCE_LOW).apply {
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type)

        cm.registerTorchCallback(torchCallback, Handler(Looper.getMainLooper()))
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter) // must be registered in code, not the manifest
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> setTorch(!torchOn)
            ACTION_REFRESH -> refreshNotification() // re-posts if the user swiped it away
        }
        return START_STICKY
    }

    private fun setTorch(on: Boolean) {
        val id = camId ?: return
        try {
            cm.setTorchMode(id, on)
            torchOn = on
            refreshNotification()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun refreshNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val toggle = PendingIntent.getService(
            this, 1,
            Intent(this, TorchService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val reappear = PendingIntent.getForegroundService(
            this, 2,
            Intent(this, TorchService::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off) // replace with your own icon
            .setContentTitle("Flashlight")
            .setContentText(if (torchOn) "On" else "Off")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setDeleteIntent(reappear)
            .setContentIntent(toggle)
            .addAction(0, if (torchOn) "Turn off" else "Turn on", toggle)
            .build()
    }

    override fun onDestroy() {
        cm.unregisterTorchCallback(torchCallback)
        unregisterReceiver(screenReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
