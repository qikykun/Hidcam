package com.example.kamera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var btnOverlay: Button
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button

    private val permissions = buildList {
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    private val requestPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnOverlay = findViewById(R.id.btnOverlay)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)

        btnOverlay.setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
        btnStart.setOnClickListener {
            if (!hasPerms()) { requestPerms.launch(permissions); return@setOnClickListener }
            send(CameraService.ACTION_START)
        }
        btnStop.setOnClickListener { send(CameraService.ACTION_STOP) }

        if (!hasPerms()) requestPerms.launch(permissions)
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        val canOverlay = Settings.canDrawOverlays(this)
        btnOverlay.visibility = if (canOverlay) View.GONE else View.VISIBLE
        btnStart.isEnabled = canOverlay
    }

    private fun hasPerms() = permissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun send(action: String) {
        val i = Intent(this, CameraService::class.java).apply { this.action = action }
        if (action == CameraService.ACTION_START)
            ContextCompat.startForegroundService(this, i)
        else
            startService(i)
    }
}
