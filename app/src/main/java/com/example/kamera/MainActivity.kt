package com.example.kamera

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var btnFloating: Button
    private lateinit var btnDrag: Button
    private lateinit var btnCamera: Button
    private lateinit var btnClose: Button
    private var dragOn = false

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

        btnFloating = findViewById(R.id.btnFloating)
        btnDrag = findViewById(R.id.btnDrag)
        btnCamera = findViewById(R.id.btnCamera)
        btnClose = findViewById(R.id.btnClose)

        btnFloating.setOnClickListener {
            if (!hasPerms()) { requestPerms.launch(permissions); return@setOnClickListener }
            if (!Settings.canDrawOverlays(this)) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
                Toast.makeText(this, "Izinkan tampil di atas aplikasi", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (CameraService.isRunning) send(CameraService.ACTION_STOP)
            else send(CameraService.ACTION_START)
            btnFloating.postDelayed({ refresh() }, 300)
        }

        btnDrag.setOnClickListener {
            if (!CameraService.isRunning) {
                Toast.makeText(this, "Nyalakan floating dulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            dragOn = !dragOn
            send(if (dragOn) CameraService.ACTION_DRAG_ON else CameraService.ACTION_DRAG_OFF)
            btnDrag.text = if (dragOn) "Geser floating" else "Pengaturan floating"
        }

        btnCamera.setOnClickListener { showCameraDialog() }

        btnClose.setOnClickListener { finish() }

        if (!hasPerms()) requestPerms.launch(permissions)
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        btnFloating.text = if (CameraService.isRunning) "Matikan floating" else "Mulai floating"
        if (!CameraService.isRunning) {
            dragOn = false
            btnDrag.text = "Pengaturan floating"
        }
    }

    private fun showCameraDialog() {
        val prefs = getSharedPreferences("cfg", Context.MODE_PRIVATE)
        val options = arrayOf("Kamera Belakang", "Kamera Depan")
        AlertDialog.Builder(this)
            .setTitle("Pilih kamera")
            .setItems(options) { _, which ->
                prefs.edit().putBoolean("front", which == 1).apply()
                if (CameraService.isRunning) send(CameraService.ACTION_SET_CAMERA)
                Toast.makeText(this, options[which], Toast.LENGTH_SHORT).show()
            }
            .show()
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
