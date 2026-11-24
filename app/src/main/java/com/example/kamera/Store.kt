package com.example.kamera

import android.content.Context
import java.io.File

/** Lokasi penyimpanan privat: di dalam folder aplikasi, jadi TIDAK muncul di galeri utama HP. */
object Store {
    fun picDir(ctx: Context): File =
        File(ctx.getExternalFilesDir(null), "Kamera/Foto").apply { mkdirs() }

    fun vidDir(ctx: Context): File =
        File(ctx.getExternalFilesDir(null), "Kamera/Video").apply { mkdirs() }

    /** Semua foto + video, terbaru di atas. */
    fun allMedia(ctx: Context): List<File> {
        val pics = picDir(ctx).listFiles()?.toList() ?: emptyList()
        val vids = vidDir(ctx).listFiles()?.toList() ?: emptyList()
        return (pics + vids)
            .filter { it.isFile }
            .sortedByDescending { it.lastModified() }
    }

    fun isVideo(f: File): Boolean = f.name.endsWith(".mp4", ignoreCase = true)
}
