package com.example.kamera

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
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

    private lateinit var windowManager: WindowManager
    private var bar: View? = null

    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var isRecording = false

    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var longPressFired = false

    companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        private const val CHANNEL = "kamera"
        private const val NOTIF_ID = 1
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> startAll()
            ACTION_STOP -> stopAll()
        }
        return START_STICKY
    }

    private fun startAll() {
        startForegroundNotif()
        acquireWakeLock()
        showBar()
        initCamera()
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun initCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val recorder = Recorder.Builder().build()
            val vcBuilder = VideoCapture.Builder(recorder)
            Camera2Interop.Extender(vcBuilder).setCaptureRequestOption(
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
            )
            videoCapture = vcBuilder.build()
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()
            provider.unbindAll()
            provider.bindToLifecycle(
                this, CameraSelector.DEFAULT_BACK_CAMERA, videoCapture, imageCapture
            )
        }, ContextCompat.getMainExecutor(this))
    }

    private fun showBar() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val view = View(this)
        view.isClickable = true
        setBarColor(view, false)

        val params = WindowManager.LayoutParams(
            dp(14), dp(90),
            if (Build.VERSION.SDK_INT >= 26)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.END or Gravity.CENTER_VERTICAL

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    longPressFired = false
                    longPressRunnable = Runnable {
                        longPressFired = true
                        toggleRecording(v)
                    }
                    handler.postDelayed(longPressRunnable!!, 2000)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    longPressRunnable?.let { handler.removeCallbacks(it) }
                    if (!longPressFired) takePhoto()
                    true
                }
                else -> false
            }
        }

        bar = view
        windowManager.addView(view, params)
    }

    private fun setBarColor(view: View, recording: Boolean) {
        val d = GradientDrawable()
        d.cornerRadius = dp(7).toFloat()
        d.setColor(
            if (recording) Color.argb(220, 255, 255, 255)
            else Color.argb(90, 255, 255, 255)
        )
        view.background = d
    }

    private fun toggleRecording(v: View) {
        if (isRecording) {
            recording?.stop()
            recording = null
            isRecording = false
            setBarColor(v, false)
        } else {
            startRecording()
            isRecording = true
            setBarColor(v, true)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startRecording() {
        val vc = videoCapture ?: return
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "KAM_" + stamp())
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/Kamera")
        }
        val output = MediaStoreOutputOptions.Builder(
            contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(values).build()

        recording = vc.output
            .prepareRecording(this, output)
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(this)) { }
    }

    private fun takePhoto() {
        val ic = imageCapture ?: return
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_" + stamp())
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Kamera")
        }
        val output = ImageCapture.OutputFileOptions.Builder(
            contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        ).build()

        ic.takePicture(output, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(r: ImageCapture.OutputFileResults) {}
                override fun onError(e: ImageCaptureException) {}
            })
    }

    private fun stamp() = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
        .format(System.currentTimeMillis())

    private fun stopAll() {
        recording?.stop()
        recording = null
        bar?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        bar = null
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

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        bar?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
    }
}
