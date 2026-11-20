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
    private var barParams: WindowManager.LayoutParams? = null

    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var isRecording = false

    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())

    private var repositionRunnable: Runnable? = null
    private var videoRunnable: Runnable? = null
    private var downX = 0f
    private var lastRawY = 0f
    private var dragStartRawY = 0f
    private var initialBarY = 0
    private var hasSlidLeft = false
    private var dragMode = false
    private var videoToggled = false

    companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        private const val CHANNEL = "kamera"
        private const val NOTIF_ID = 1
        private const val MODE_IDLE = 0
        private const val MODE_RECORDING = 1
        private const val MODE_DRAG = 2
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
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
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
        colorFor(view, MODE_IDLE)

        val params = WindowManager.LayoutParams(
            dp(12), dp(68),
            if (Build.VERSION.SDK_INT >= 26)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        barParams = params

        val slideThreshold = dp(24).toFloat()

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    lastRawY = event.rawY
                    hasSlidLeft = false
                    dragMode = false
                    videoToggled = false
                    repositionRunnable = Runnable {
                        if (!hasSlidLeft) {
                            dragMode = true
                            dragStartRawY = lastRawY
                            initialBarY = barParams?.y ?: 0
                            colorFor(v, MODE_DRAG)
                        }
                    }
                    handler.postDelayed(repositionRunnable!!, 2000)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    lastRawY = event.rawY
                    val dx = event.rawX - downX
                    if (dragMode) {
                        val p = barParams
                        if (p != null) {
                            p.y = initialBarY + (event.rawY - dragStartRawY).toInt()
                            windowManager.updateViewLayout(v, p)
                        }
                    } else if (!hasSlidLeft && dx < -slideThreshold) {
                        hasSlidLeft = true
                        repositionRunnable?.let { handler.removeCallbacks(it) }
                        videoRunnable = Runnable {
                            videoToggled = true
                            toggleRecording(v)
                        }
                        handler.postDelayed(videoRunnable!!, 1000)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    repositionRunnable?.let { handler.removeCallbacks(it) }
                    videoRunnable?.let { handler.removeCallbacks(it) }
                    if (dragMode) {
                        dragMode = false
                        colorFor(v, if (isRecording) MODE_RECORDING else MODE_IDLE)
                    } else if (hasSlidLeft && !videoToggled) {
                        takePhoto()
                        pulse(v, 1.7f, 70)
                    }
                    true
                }
                else -> false
            }
        }

        bar = view
        windowManager.addView(view, params)
    }

    private fun colorFor(v: View, mode: Int) {
        val alpha = when (mode) {
            MODE_RECORDING -> 160
            MODE_DRAG -> 100
            else -> 35
        }
        val d = GradientDrawable()
        d.cornerRadius = dp(6).toFloat()
        d.setColor(Color.argb(alpha, 255, 255, 255))
        v.background = d
    }

    private fun pulse(v: View, scale: Float, dur: Long) {
        v.pivotX = v.width.toFloat()
        v.animate().scaleX(scale).setDuration(dur).withEndAction {
            v.animate().scaleX(1f).setDuration(dur).start()
        }.start()
    }

    private fun toggleRecording(v: View) {
        if (isRecording) {
            recording?.stop()
            recording = null
            isRecording = false
            colorFor(v, MODE_IDLE)
            pulse(v, 2.4f, 140)
        } else {
            startRecording()
            isRecording = true
            colorFor(v, MODE_RECORDING)
            pulse(v, 2.4f, 140)
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
