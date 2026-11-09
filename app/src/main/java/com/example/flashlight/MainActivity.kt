package com.example.flashlight

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var cm: CameraManager
    private var camId: String? = null
    private var torchOn = false
    private lateinit var circle: ImageView
    private lateinit var status: TextView
    private val circleBg = GradientDrawable().apply { shape = GradientDrawable.OVAL }

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == camId) {
                torchOn = enabled
                render()
            }
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        camId = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
        ContextCompat.startForegroundService(this, Intent(this, TorchService::class.java))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#16181D"))
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }

        fun spacer() = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
        }

        val title = TextView(this).apply {
            text = "Flashlight"
            textSize = 26f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        circle = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(220), dp(220)).apply {
                topMargin = dp(40)
                bottomMargin = dp(24)
            }
            setPadding(dp(64), dp(64), dp(64), dp(64))
            background = circleBg
            setImageResource(R.drawable.ic_torch)
            setOnClickListener {
                ContextCompat.startForegroundService(
                    this@MainActivity,
                    Intent(this@MainActivity, TorchService::class.java)
                        .setAction(TorchService.ACTION_TOGGLE)
                )
            }
        }

        status = TextView(this).apply {
            textSize = 18f
            gravity = Gravity.CENTER
        }

        val hint = TextView(this).apply {
            text = "Press the power button while the screen is off to switch the light off."
            textSize = 13f
            setTextColor(Color.parseColor("#7C828D"))
            gravity = Gravity.CENTER
        }

        root.addView(spacer())
        root.addView(title)
        root.addView(circle)
        root.addView(status)
        root.addView(spacer())
        root.addView(hint)
        setContentView(root)
        render()
    }

    private fun render() {
        if (!::circle.isInitialized) return
        circleBg.setColor(Color.parseColor(if (torchOn) "#FFC107" else "#2A2D35"))
        circle.setColorFilter(Color.parseColor(if (torchOn) "#16181D" else "#9AA0AA"))
        status.setTextColor(Color.parseColor(if (torchOn) "#FFC107" else "#9AA0AA"))
        status.text = if (torchOn) "ON" else "OFF - tap to turn on"
    }

    override fun onStart() {
        super.onStart()
        cm.registerTorchCallback(torchCallback, Handler(Looper.getMainLooper()))
    }

    override fun onStop() {
        cm.unregisterTorchCallback(torchCallback)
        super.onStop()
    }
}
