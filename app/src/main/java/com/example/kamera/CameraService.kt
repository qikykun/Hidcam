package com.example.kamera

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.os.PowerManager
import android.provider.MediaStore
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import java.text.SimpleDateFormat
import java.util.Locale

class CameraService : LifecycleService() {

    private var recording: Recording? = null
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        private const val CHANNEL = "kamera"
        private const val NOTIF_ID = 1
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> start()
            ACTION_STOP -> stop()
        }
        return START_STICKY
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun start() {
        startForegroundNotif()
        acquireWakeLock()
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val recorder = Recorder.Builder().build()

            val builder = VideoCapture.Builder(recorder)
            Camera2Interop.Extender(builder).setCaptureRequestOption(
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
            )
            val videoCapture = builder.build()

            provider.unbindAll()
            provider.bindToLifecycle(
                this, CameraSelector.DEFAULT_BACK_CAMERA, videoCapture
            )
            startRecording(videoCapture)
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("MissingPermission")
    private fun startRecording(videoCapture: VideoCapture<Recorder>) {
        val name = "KAM_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            .format(System.currentTimeMillis())
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/Kamera")
        }
        val output = MediaStoreOutputOptions.Builder(
            contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(values).build()

        recording = videoCapture.output
            .prepareRecording(this, output)
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(this)) { }
    }

    private fun stop() {
        recording?.stop()
        recording = null
        wakeLock?.let { if (it.isHeld) it.release() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundNotif() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Kamera", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("Kamera aktif")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
        val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        startForeground(NOTIF_ID, notif, type)
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "kamera:rec").also {
            it.acquire(60 * 60 * 1000L)
        }
    }
}
