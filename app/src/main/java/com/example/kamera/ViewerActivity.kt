package com.example.kamera

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class ViewerActivity : AppCompatActivity() {

    private lateinit var file: File
    private var isVideo = false
    private lateinit var video: VideoView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_viewer)

        file = File(intent.getStringExtra("path") ?: "")
        isVideo = intent.getBooleanExtra("video", false)

        val img = findViewById<ImageView>(R.id.img)
        video = findViewById(R.id.video)

        if (isVideo) {
            img.visibility = View.GONE
            video.visibility = View.VISIBLE
            video.setMediaController(MediaController(this))
            video.setVideoPath(file.absolutePath)
            video.setOnPreparedListener { video.start() }
        } else {
            video.visibility = View.GONE
            img.visibility = View.VISIBLE
            img.setImageBitmap(decode())
        }

        findViewById<Button>(R.id.btnMove).setOnClickListener { moveToGallery() }
        findViewById<Button>(R.id.btnDelete).setOnClickListener { confirmDelete() }
    }

    private fun decode(): Bitmap? = try {
        val dm = resources.displayMetrics
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, o)
        var s = 1
        while (o.outWidth / s > dm.widthPixels || o.outHeight / s > dm.heightPixels) s *= 2
        BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = s })
    } catch (e: Exception) {
        null
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Hapus")
            .setMessage("Hapus file ini?")
            .setPositiveButton("Hapus") { _, _ ->
                if (isVideo) video.stopPlayback()
                file.delete()
                Toast.makeText(this, "Dihapus", Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun moveToGallery() {
        try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, if (isVideo) "video/mp4" else "image/jpeg")
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    if (isVideo) "Movies/Kamera" else "Pictures/Kamera"
                )
            }
            val collection = if (isVideo)
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val uri: Uri = contentResolver.insert(collection, values) ?: return
            contentResolver.openOutputStream(uri).use { out ->
                file.inputStream().use { input -> input.copyTo(out!!) }
            }
            if (isVideo) video.stopPlayback()
            file.delete()
            Toast.makeText(this, "Dipindah ke galeri HP", Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal memindah", Toast.LENGTH_SHORT).show()
        }
    }
}
