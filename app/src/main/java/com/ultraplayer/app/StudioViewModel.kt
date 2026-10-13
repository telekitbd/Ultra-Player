package com.ultraplayer.app

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class StudioViewModel(app: Application) : AndroidViewModel(app) {
    private val sp = app.getSharedPreferences("studio", 0)

    var videoUri by mutableStateOf<Uri?>(null)
    var videoName by mutableStateOf("")
    var subtitleName by mutableStateOf("")
    var urlText by mutableStateOf("")
    var sourceLang by mutableStateOf(
        sp.getString("source", sp.getString("spoken", "auto")) ?: "auto",
    )
    var targetLang by mutableStateOf(sp.getString("target", "bn") ?: "bn")
    var busy by mutableStateOf(false)
    var status by mutableStateOf("")
    var progress by mutableFloatStateOf(0f)
    var errorMsg by mutableStateOf<String?>(null)
    var cues by mutableStateOf<List<Cue>>(emptyList())
    var original by mutableStateOf<List<Cue>>(emptyList())
    var translated by mutableStateOf(false)

    private var job: Job? = null

    init {
        saveSettings()
    }

    fun saveSettings() {
        sp.edit()
            .remove("provider")
            .remove("groq_key")
            .remove("gemini_key")
            .remove("key")
            .remove("endpoint")
            .remove("model")
            .remove("spoken")
            .putString("source", sourceLang)
            .putString("target", targetLang)
            .apply()
    }

    fun setVideo(uri: Uri, name: String) {
        videoUri = uri
        videoName = name
        urlText = ""
    }

    fun importSubtitle(uri: Uri, name: String) {
        runCatching {
            val text = getApplication<Application>().contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: throw IllegalStateException("Subtitle file could not be opened")
            val list = Subs.parse(text)
            if (list.isEmpty()) throw IllegalStateException("No readable SRT/VTT subtitle cues found")
            original = list
            cues = list
            translated = false
            subtitleName = name
            status = "${list.size} subtitle lines loaded"
            errorMsg = null
        }.onFailure {
            errorMsg = it.message ?: it.toString()
        }
    }

    fun cancel() {
        job?.cancel()
    }

    fun translateSubtitle() {
        if (original.isEmpty()) {
            errorMsg = "Open an SRT or VTT subtitle file first"
            return
        }
        saveSettings()
        job = viewModelScope.launch {
            busy = true
            errorMsg = null
            progress = 0f
            try {
                status = "Preparing on-device translation..."
                cues = CueTranslator.translate(original, sourceLang, targetLang) {
                    progress = it
                    status = "Translating subtitle: ${(it * 100).toInt()}%"
                }
                translated = true
                progress = 1f
                status = "Translated: ${cues.size} subtitle lines"
            } catch (e: CancellationException) {
                status = "Cancelled"
                throw e
            } catch (e: Exception) {
                errorMsg = e.message ?: e.toString()
            } finally {
                busy = false
            }
        }
    }

    private fun baseName(): String {
        val b = videoName.ifBlank { subtitleName }.substringBeforeLast('.').ifBlank { "subtitle" }
        return if (translated) "$b.$targetLang" else b
    }

    fun export(fmt: String): String {
        val text = when (fmt) {
            "srt" -> Subs.toSrt(cues)
            "vtt" -> Subs.toVtt(cues)
            "ass" -> Subs.toAss(cues)
            else -> Subs.toTxt(cues)
        }
        val mime = when (fmt) { "vtt" -> "text/vtt"; else -> "text/plain" }
        val path = saveToDownloads(getApplication(), "${baseName()}.$fmt", mime, text.toByteArray())
        return "Saved: $path"
    }

    /** Writes an SRT to cache so the player can load it. */
    fun cacheSrt(): String {
        val f = File(getApplication<Application>().cacheDir, "studio_play.srt")
        f.writeText(Subs.toSrt(cues))
        return f.absolutePath
    }
}
