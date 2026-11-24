package com.example.kamera

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Size
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.util.concurrent.Executors

class GalleryActivity : AppCompatActivity() {

    private lateinit var grid: GridView
    private lateinit var empty: TextView
    private lateinit var selBar: LinearLayout
    private lateinit var btnSelectAll: Button
    private lateinit var btnDeleteSel: Button
    private lateinit var btnCancel: Button

    private var items: List<File> = emptyList()
    private val selected = HashSet<String>()
    private var selectionMode = false
    private var gestureInSel = false
    private var lastDragPos = -1
    private var dragMoved = false

    private val io = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())
    private val side by lazy { resources.displayMetrics.widthPixels / 3 }

    private val idImg = 101
    private val idDur = 102
    private val idCheck = 103

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)
        applyInsets(findViewById(R.id.root))

        grid = findViewById(R.id.grid)
        empty = findViewById(R.id.empty)
        selBar = findViewById(R.id.selBar)
        btnSelectAll = findViewById(R.id.btnSelectAll)
        btnDeleteSel = findViewById(R.id.btnDeleteSel)
        btnCancel = findViewById(R.id.btnCancel)

        grid.setOnItemClickListener { _, _, pos, _ ->
            if (!selectionMode) openViewer(pos)
        }
        grid.setOnItemLongClickListener { _, _, pos, _ ->
            if (!selectionMode) enterSelection()
            addSelect(pos)
            true
        }
        grid.setOnTouchListener { _, e ->
            if (!selectionMode) return@setOnTouchListener false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    gestureInSel = true
                    lastDragPos = -1
                    dragMoved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val pos = grid.pointToPosition(e.x.toInt(), e.y.toInt())
                    if (pos != AdapterView.INVALID_POSITION && pos != lastDragPos) {
                        addSelect(pos)
                        lastDragPos = pos
                        dragMoved = true
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (gestureInSel && !dragMoved) {
                        val pos = grid.pointToPosition(e.x.toInt(), e.y.toInt())
                        if (pos != AdapterView.INVALID_POSITION) toggle(pos)
                    }
                    gestureInSel = false
                    true
                }
                else -> true
            }
        }

        btnSelectAll.setOnClickListener {
            if (selected.size == items.size) selected.clear()
            else { selected.clear(); items.forEach { selected.add(it.absolutePath) } }
            updateCount()
            refreshGrid()
        }
        btnDeleteSel.setOnClickListener { confirmDeleteSelected() }
        btnCancel.setOnClickListener { exitSelection() }
    }

    private fun applyInsets(v: View) {
        ViewCompat.setOnApplyWindowInsetsListener(v) { view, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
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

    private fun refreshGrid() {
        (grid.adapter as? Adapter)?.notifyDataSetChanged()
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
        selBar.visibility = View.VISIBLE
    }

    private fun exitSelection() {
        selectionMode = false
        selected.clear()
        selBar.visibility = View.GONE
        refreshGrid()
    }

    private fun addSelect(pos: Int) {
        selected.add(items[pos].absolutePath)
        updateCount()
        refreshGrid()
    }

    private fun toggle(pos: Int) {
        val path = items[pos].absolutePath
        if (!selected.remove(path)) selected.add(path)
        updateCount()
        refreshGrid()
    }

    private fun updateCount() {
        btnDeleteSel.text = "Hapus (" + selected.size + ")"
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

    override fun onBackPressed() {
        if (selectionMode) exitSelection() else super.onBackPressed()
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
                    id = idDur
                    setTextColor(Color.WHITE)
                    textSize = 11f
                    setBackgroundColor(Color.parseColor("#99000000"))
                    setPadding(8, 2, 8, 2)
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM or Gravity.END
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
            val dur = frame.findViewById<TextView>(idDur)
            val check = frame.findViewById<TextView>(idCheck)
            val f = items[pos]
            val sel = selected.contains(f.absolutePath)
            val video = Store.isVideo(f)

            img.alpha = if (sel) 0.4f else 1f
            check.visibility = if (sel) View.VISIBLE else View.GONE
            dur.visibility = if (video) View.VISIBLE else View.GONE
            dur.text = ""

            img.setImageDrawable(null)
            img.tag = f.absolutePath
            io.execute {
                val bmp = thumb(f)
                val d = if (video) "▶ " + fmtDur(videoDuration(f)) else null
                main.post {
                    if (img.tag == f.absolutePath) {
                        img.setImageBitmap(bmp)
                        if (d != null) dur.text = d
                    }
                }
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

    private fun videoDuration(f: File): Long = try {
        val r = MediaMetadataRetriever()
        r.setDataSource(f.absolutePath)
        val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        r.release()
        ms
    } catch (e: Exception) {
        0L
    }

    private fun fmtDur(ms: Long): String {
        val s = (ms / 1000).toInt()
        return "%d:%02d".format(s / 60, s % 60)
    }

    override fun onDestroy() {
        super.onDestroy()
        io.shutdownNow()
    }
}
