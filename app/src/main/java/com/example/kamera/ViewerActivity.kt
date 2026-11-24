package com.example.kamera

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import kotlin.math.abs

class ViewerActivity : AppCompatActivity() {

    private lateinit var paths: ArrayList<String>
    private var index = 0

    private lateinit var img: ImageView
    private lateinit var video: VideoView
    private lateinit var gesture: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_viewer)

        paths = intent.getStringArrayListExtra("paths") ?: arrayListOf()
        index = intent.getIntExtra("index", 0)

        img = findViewById(R.id.img)
        video = findViewById(R.id.video)

        gesture = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float
            ): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (abs(dx) > abs(dy) && abs(dx) > 120) {
                    if (dx < 0) goTo(index + 1) else goTo(index - 1)
                    return true
                }
                return false
            }
        })
        img.setOnTouchListener { _, e -> gesture.onTouchEvent(e); true }
        video.setOnTouchListener { _, e -> gesture.onTouchEvent(e); false }

        findViewById<Button>(R.id.btnMove).setOnClickListener { moveToGallery() }
        findViewById<Button>(R.id.btnDelete).setOnClickListener { confirmDelete() }

        if (paths.isEmpty()) finish() else showCurrent()
    }

    private fun goTo(newIndex: Int) {
        if (newIndex < 0 || newIndex >= paths.size) return
        index = newIndex
        showCurrent()
    }

    private fun current() = File(paths[index])
    private fun isVideo() = Store.isVideo(current())

    private fun showCurrent() {
        video.stopPlayback()
        if (isVideo()) {
            img.visibility = View.GONE
            video.visibility = View.VISIBLE
            video.setMediaController(MediaController(this))
            video.setVideoPath(current().absolutePath)
            video.setOnPreparedListener { video.start() }
        } else {
            video.visibility = View.GONE
            img.visibility = View.VISIBLE
            img.setImageBitmap(decode())
        }
    }

    private fun decode(): Bitmap? = try {
        val dm = resources.displayMetrics
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(current().absolutePath, o)
        var s = 1
        while (o.outWidth / s > dm.widthPixels || o.outHeight / s > dm.heightPixels) s *= 2
        BitmapFactory.decodeFile(current().absolutePath, BitmapFactory.Options().apply { inSampleSize = s })
    } catch (e: Exception) {
        null
    }

    private fun afterRemove() {
        if (paths.isEmpty()) { finish(); return }
        if (index >= paths.size) index = paths.size - 1
        showCurrent()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Hapus")
            .setMessage("Hapus file ini?")
            .setPositiveButton("Hapus") { _, _ ->
                video.stopPlayback()
                current().delete()
                paths.removeAt(index)
                Toast.makeText(this, "Dihapus", Toast.LENGTH_SHORT).show()
                afterRemove()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun moveToGallery() {
        try {
            val f = current()
            val vid = isVideo()
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, f.name)
                put(MediaStore.MediaColumns.MIME_TYPE, if (vid) "video/mp4" else "image/jpeg")
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    if (vid) "Movies/Kamera" else "Pictures/Kamera"
                )
            }
            val collection = if (vid)
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val uri: Uri = contentResolver.insert(collection, values) ?: return
            contentResolver.openOutputStream(uri).use { out ->
                f.inputStream().use { input -> input.copyTo(out!!) }
            }
            video.stopPlayback()
            f.delete()
            paths.removeAt(index)
            Toast.makeText(this, "Dipindah ke galeri HP", Toast.LENGTH_SHORT).show()
            afterRemove()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal memindah", Toast.LENGTH_SHORT).show()
        }
    }
}
