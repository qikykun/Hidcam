package com.example.kamera

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
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
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.util.concurrent.Executors

class GalleryActivity : AppCompatActivity() {

    private lateinit var grid: GridView
    private lateinit var empty: TextView
    private lateinit var btnDeleteAll: Button
    private lateinit var btnDeleteSel: Button
    private lateinit var btnCancel: Button

    private var items: List<File> = emptyList()
    private val selected = HashSet<String>()
    private var selectionMode = false

    private val io = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())
    private val side by lazy { resources.displayMetrics.widthPixels / 3 }

    private val idImg = 101
    private val idPlay = 102
    private val idCheck = 103

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)
        grid = findViewById(R.id.grid)
        empty = findViewById(R.id.empty)
        btnDeleteAll = findViewById(R.id.btnDeleteAll)
        btnDeleteSel = findViewById(R.id.btnDeleteSel)
        btnCancel = findViewById(R.id.btnCancel)

        grid.setOnItemClickListener { _, _, pos, _ ->
            if (selectionMode) toggle(pos) else openViewer(pos)
        }
        grid.setOnItemLongClickListener { _, _, pos, _ ->
            if (!selectionMode) enterSelection()
            toggle(pos)
            true
        }

        btnDeleteAll.setOnClickListener { confirmDeleteAll() }
        btnDeleteSel.setOnClickListener { confirmDeleteSelected() }
        btnCancel.setOnClickListener { exitSelection() }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        items = Store.allMedia(this)
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        grid.adapter = Adapter()
    }

    private fun openViewer(pos: Int) {
        val paths = ArrayList(items.map { it.absolutePath })
        val i = Intent(this, ViewerActivity::class.java)
        i.putStringArrayListExtra("paths", paths)
        i.putExtra("index", pos)
        startActivity(i)
    }

    private fun enterSelection() {
        selectionMode = true
        btnDeleteAll.visibility = View.GONE
        btnDeleteSel.visibility = View.VISIBLE
        btnCancel.visibility = View.VISIBLE
    }

    private fun exitSelection() {
        selectionMode = false
        selected.clear()
        btnDeleteAll.visibility = View.VISIBLE
        btnDeleteSel.visibility = View.GONE
        btnCancel.visibility = View.GONE
        (grid.adapter as? Adapter)?.notifyDataSetChanged()
    }

    private fun toggle(pos: Int) {
        val path = items[pos].absolutePath
        if (!selected.remove(path)) selected.add(path)
        btnDeleteSel.text = "Hapus (" + selected.size + ")"
        (grid.adapter as? Adapter)?.notifyDataSetChanged()
    }

    private fun confirmDeleteSelected() {
        if (selected.isEmpty()) { exitSelection(); return }
        AlertDialog.Builder(this)
            .setTitle("Hapus")
            .setMessage("Hapus " + selected.size + " item terpilih?")
            .setPositiveButton("Hapus") { _, _ ->
                selected.forEach { File(it).delete() }
                exitSelection()
                reload()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun confirmDeleteAll() {
        if (items.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("Hapus Semua")
            .setMessage("Hapus SEMUA foto & video (" + items.size + ")?")
            .setPositiveButton("Hapus Semua") { _, _ ->
                items.forEach { it.delete() }
                reload()
            }
            .setNegativeButton("Batal", null)
            .show()
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
                    setTextColor(Color.WHITE)
                    textSize = 24f
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER
                    )
                })
                addView(TextView(context).apply {
                    id = idCheck
                    text = "✓"
                    setTextColor(Color.WHITE)
                    textSize = 16f
                    setBackgroundColor(Color.parseColor("#CC2196F3"))
                    setPadding(10, 2, 10, 2)
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP or Gravity.END
                    )
                })
            }
            val img = frame.findViewById<ImageView>(idImg)
            val play = frame.findViewById<TextView>(idPlay)
            val check = frame.findViewById<TextView>(idCheck)
            val f = items[pos]
            val sel = selected.contains(f.absolutePath)

            img.alpha = if (sel) 0.4f else 1f
            check.visibility = if (sel) View.VISIBLE else View.GONE
            play.visibility = if (Store.isVideo(f)) View.VISIBLE else View.GONE

            img.setImageDrawable(null)
            img.tag = f.absolutePath
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
