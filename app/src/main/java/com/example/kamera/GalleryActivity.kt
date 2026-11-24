package com.example.kamera

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Size
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.util.concurrent.Executors

class GalleryActivity : AppCompatActivity() {

    private lateinit var grid: GridView
    private lateinit var empty: TextView
    private var items: List<File> = emptyList()
    private val io = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())
    private val side by lazy { resources.displayMetrics.widthPixels / 3 }

    private val idImg = 101
    private val idPlay = 102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)
        grid = findViewById(R.id.grid)
        empty = findViewById(R.id.empty)
        grid.setOnItemClickListener { _, _, pos, _ ->
            val f = items[pos]
            val i = Intent(this, ViewerActivity::class.java)
            i.putExtra("path", f.absolutePath)
            i.putExtra("video", Store.isVideo(f))
            startActivity(i)
        }
    }

    override fun onResume() {
        super.onResume()
        items = Store.allMedia(this)
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        grid.adapter = Adapter()
    }

    inner class Adapter : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(p: Int) = items[p]
        override fun getItemId(p: Int) = p.toLong()

        override fun getView(pos: Int, convert: View?, parent: ViewGroup): View {
            val frame = convert as? FrameLayout ?: FrameLayout(this@GalleryActivity).apply {
                layoutParams = AbsListView.LayoutParams(side, side)
                addView(ImageView(context).apply {
                    id = idImg
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                })
                addView(TextView(context).apply {
                    id = idPlay
                    text = "▶"
                    setTextColor(0xFFFFFFFF.toInt())
                    textSize = 24f
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER
                    )
                })
            }
            val img = frame.findViewById<ImageView>(idImg)
            val play = frame.findViewById<TextView>(idPlay)
            val f = items[pos]
            img.setImageDrawable(null)
            img.tag = f.absolutePath
            play.visibility = if (Store.isVideo(f)) View.VISIBLE else View.GONE
            io.execute {
                val bmp = thumb(f)
                main.post { if (img.tag == f.absolutePath) img.setImageBitmap(bmp) }
            }
            return frame
        }
    }

    private fun thumb(f: File): Bitmap? = try {
        if (Store.isVideo(f)) {
            ThumbnailUtils.createVideoThumbnail(f, Size(side, side), null)
        } else {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.absolutePath, o)
            var s = 1
            while (o.outWidth / s > side || o.outHeight / s > side) s *= 2
            BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = s })
        }
    } catch (e: Exception) {
        null
    }

    override fun onDestroy() {
        super.onDestroy()
        io.shutdownNow()
    }
}
