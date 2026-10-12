package com.ultraplayer.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File

fun fmtTime(ms: Long): String {
    val t = ms.coerceAtLeast(0) / 1000
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

fun fmtSize(b: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB")
    var v = b.toDouble()
    var i = 0
    while (v >= 1024 && i < 3) { v /= 1024; i++ }
    return "%.1f %s".format(v, units[i])
}

fun resLabel(w: Int, h: Int): String {
    val m = maxOf(w, h)
    return when {
        m >= 7680 -> "8K"
        m >= 3840 -> "4K"
        m >= 2560 -> "2K"
        m >= 1920 -> "FHD"
        m >= 1280 -> "HD"
        m > 0 -> "SD"
        else -> ""
    }
}

fun displayName(ctx: Context, uri: Uri): String {
    if (uri.scheme == "content") {
        runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) return it.getString(0)
            }
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/') ?: "Video"
}

fun copyToCache(ctx: Context, uri: Uri, name: String): File {
    val f = File(ctx.cacheDir, name)
    ctx.contentResolver.openInputStream(uri)!!.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
    return f
}

/** Saves into Downloads/UltraPlayer and returns a readable path. */
fun saveToDownloads(ctx: Context, name: String, mime: String, data: ByteArray): String {
    if (Build.VERSION.SDK_INT >= 29) {
        val v = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/UltraPlayer")
        }
        val u = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)
            ?: throw IllegalStateException("Save failed")
        ctx.contentResolver.openOutputStream(u)!!.use { it.write(data) }
        return "Download/UltraPlayer/$name"
    }
    val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!
    val f = File(dir, name)
    f.writeBytes(data)
    return f.absolutePath
}

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("resume", Context.MODE_PRIVATE)
    fun getResume(c: Context, key: String): Long = sp(c).getLong(key, 0L)
    fun setResume(c: Context, key: String, v: Long) = sp(c).edit().putLong(key, v).apply()
    fun clearResume(c: Context, key: String) = sp(c).edit().remove(key).apply()
}
