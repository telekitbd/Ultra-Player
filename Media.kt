package com.ultraplayer.app

import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GalleryItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val folder: String,
    val durationMs: Long,
    val size: Long,
    val dateAdded: Long,
    val width: Int,
    val height: Int,
    val isVideo: Boolean,
)

object MediaRepository {
    suspend fun load(ctx: Context): List<GalleryItem> = withContext(Dispatchers.IO) {
        query(ctx, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true) +
            query(ctx, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false)
    }

    private fun query(ctx: Context, base: Uri, video: Boolean): List<GalleryItem> {
        val out = ArrayList<GalleryItem>()
        val proj = arrayListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            "bucket_display_name",
        )
        if (video) proj += MediaStore.Video.Media.DURATION
        runCatching {
            ctx.contentResolver.query(
                base, proj.toTypedArray(), null, null,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val iName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val iSize = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val iDate = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val iW = c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
                val iH = c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
                val iB = c.getColumnIndexOrThrow("bucket_display_name")
                val iD = if (video) c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION) else -1
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    out += GalleryItem(
                        id = id,
                        uri = ContentUris.withAppendedId(base, id),
                        name = c.getString(iName) ?: "Unknown",
                        folder = c.getString(iB) ?: "Other",
                        durationMs = if (iD >= 0) c.getLong(iD) else 0L,
                        size = c.getLong(iSize),
                        dateAdded = c.getLong(iDate),
                        width = c.getInt(iW),
                        height = c.getInt(iH),
                        isVideo = video,
                    )
                }
            }
        }
        return out
    }
}

class GalleryViewModel(app: Application) : AndroidViewModel(app) {
    var items by mutableStateOf<List<GalleryItem>>(emptyList())
    var loading by mutableStateOf(false)
    var tab by mutableIntStateOf(0)          // 0 videos, 1 photos
    var folder by mutableStateOf<String?>(null)
    var query by mutableStateOf("")
    var sort by mutableIntStateOf(0)         // 0 date, 1 name, 2 size, 3 duration

    fun refresh() {
        viewModelScope.launch {
            loading = true
            items = MediaRepository.load(getApplication())
            loading = false
        }
    }

    private fun byTab() = items.filter { it.isVideo == (tab == 0) }

    fun folders(): List<String> = byTab().map { it.folder }.distinct().sorted()

    fun visible(): List<GalleryItem> {
        var l = byTab()
        folder?.let { f -> l = l.filter { it.folder == f } }
        if (query.isNotBlank()) l = l.filter { it.name.contains(query, ignoreCase = true) }
        return when (sort) {
            1 -> l.sortedBy { it.name.lowercase() }
            2 -> l.sortedByDescending { it.size }
            3 -> l.sortedByDescending { it.durationMs }
            else -> l.sortedByDescending { it.dateAdded }
        }
    }
}
