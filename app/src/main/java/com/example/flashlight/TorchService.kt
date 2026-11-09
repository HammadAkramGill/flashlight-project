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
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.Display
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
    private lateinit var dm: DisplayManager
    private val handler = Handler(Looper.getMainLooper())
    private var camId: String? = null
    private var torchOn = false
    private var torchOnAt = 0L
    private var lastDisplayOn = true

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == camId) {
                if (enabled && !torchOn) torchOnAt = SystemClock.elapsedRealtime()
                torchOn = enabled
                refreshNotification()
            }
        }
    }

    // The screen woke up (power button, etc.): switch the torch off.
    // Two independent triggers are used because some phones are unreliable with one of them.
    private fun screenTurnedOn() {
        if (torchOn && SystemClock.elapsedRealtime() - torchOnAt > 1000) {
            setTorch(false)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_ON) screenTurnedOn()
        }
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            if (displayId != Display.DEFAULT_DISPLAY) return
            val display = dm.getDisplay(displayId) ?: return
            val isOn = display.state == Display.STATE_ON
            if (isOn && !lastDisplayOn) screenTurnedOn()
            lastDisplayOn = isOn
        }
    }

    override fun onCreate() {
        super.onCreate()
        cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        dm = getSystemService(DisplayManager::class.java)
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

        cm.registerTorchCallback(torchCallback, handler)
        lastDisplayOn = dm.getDisplay(Display.DEFAULT_DISPLAY)?.state == Display.STATE_ON
        dm.registerDisplayListener(displayListener, handler)
        registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_ON))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> setTorch(!torchOn)
            ACTION_REFRESH -> refreshNotification()
        }
        return START_STICKY
    }

    private fun setTorch(on: Boolean) {
        val id = camId ?: return
        try {
            if (on) torchOnAt = SystemClock.elapsedRealtime()
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
            .setSmallIcon(R.drawable.ic_torch)
            .setContentTitle("Flashlight")
            .setContentText(if (torchOn) "On - tap to turn off" else "Off - tap to turn on")
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
        dm.unregisterDisplayListener(displayListener)
        unregisterReceiver(screenReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
