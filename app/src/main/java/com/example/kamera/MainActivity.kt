package com.example.kamera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var btnStart: Button
    private lateinit var btnStop: Button

    private val permissions = buildList {
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    private val requestPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        showRecording(false)

        btnStart.setOnClickListener {
            if (hasPerms()) {
                send(CameraService.ACTION_START)
                showRecording(true)
            } else {
                requestPerms.launch(permissions)
            }
        }
        btnStop.setOnClickListener {
            send(CameraService.ACTION_STOP)
            showRecording(false)
        }

        requestPerms.launch(permissions)
    }

    private fun showRecording(recording: Boolean) {
        btnStart.visibility = if (recording) View.GONE else View.VISIBLE
        btnStop.visibility = if (recording) View.VISIBLE else View.GONE
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
