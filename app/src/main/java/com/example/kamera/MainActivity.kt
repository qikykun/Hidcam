package com.example.kamera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val permissions = buildList {
        add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    private val requestPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            if (hasPerms()) send(CameraService.ACTION_START)
            else requestPerms.launch(permissions)
        }
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            send(CameraService.ACTION_STOP)
        }

        requestPerms.launch(permissions)
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
