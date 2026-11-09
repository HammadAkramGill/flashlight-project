package com.example.flashlight

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
        ContextCompat.startForegroundService(this, Intent(this, TorchService::class.java))

        setContentView(Button(this).apply {
            text = "Toggle flashlight"
            textSize = 24f
            setOnClickListener {
                startService(Intent(this@MainActivity, TorchService::class.java)
                    .setAction(TorchService.ACTION_TOGGLE))
            }
        })
    }
}
