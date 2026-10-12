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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StudioViewModel(app: Application) : AndroidViewModel(app) {
    private val sp = app.getSharedPreferences("studio", 0)

    var videoUri by mutableStateOf<Uri?>(null)
    var videoName by mutableStateOf("")
    var urlText by mutableStateOf("")

    var apiKey by mutableStateOf(sp.getString("key", "") ?: "")
    var endpoint by mutableStateOf(sp.getString("endpoint", WhisperClient.GROQ_URL) ?: WhisperClient.GROQ_URL)
    var model by mutableStateOf(sp.getString("model", WhisperClient.GROQ_MODEL) ?: WhisperClient.GROQ_MODEL)
    var spokenLang by mutableStateOf(sp.getString("spoken", "auto") ?: "auto")
    var targetLang by mutableStateOf(sp.getString("target", "bn") ?: "bn")
    var translateEnabled by mutableStateOf(true)

    var busy by mutableStateOf(false)
    var status by mutableStateOf("")
    var progress by mutableFloatStateOf(0f)
    var errorMsg by mutableStateOf<String?>(null)
    var cues by mutableStateOf<List<Cue>>(emptyList())
    var original by mutableStateOf<List<Cue>>(emptyList())
    var translated by mutableStateOf(false)

    private var job: Job? = null

    fun saveSettings() {
        sp.edit().putString("key", apiKey).putString("endpoint", endpoint).putString("model", model)
            .putString("spoken", spokenLang).putString("target", targetLang).apply()
    }

    fun setVideo(uri: Uri, name: String) {
        videoUri = uri
        videoName = name
        urlText = ""
    }

    fun importSubtitle(uri: Uri, name: String) {
        runCatching {
            val text = getApplication<Application>().contentResolver.openInputStream(uri)!!
                .bufferedReader().use { it.readText() }
            val list = Subs.parse(text)
            if (list.isEmpty()) throw IllegalStateException("Subtitle parse kora gelo na")
            original = list; cues = list; translated = false
            if (videoName.isBlank()) videoName = name
            status = "${list.size} lines loaded"
            errorMsg = null
        }.onFailure { errorMsg = it.message }
    }

    fun cancel() { job?.cancel() }

    fun generate() {
        saveSettings()
        job = viewModelScope.launch {
            busy = true; errorMsg = null; cues = emptyList(); translated = false; progress = 0f
            val ctx = getApplication<Application>()
            try {
                val src = videoUri
                    ?: urlText.trim().takeIf { it.startsWith("http") }?.let { Uri.parse(it) }
                    ?: throw IllegalStateException("Age video select koro ba URL dao")
                if (apiKey.isBlank()) throw IllegalStateException("API key dao (Groq ba OpenAI)")

                val all = ArrayList<Cue>()
                withContext(Dispatchers.IO) {
                    status = "Audio extract hocche..."
                    AudioChunker.process(
                        ctx, src, 600,
                        onProgress = { progress = it * 0.8f },
                    ) { idx, file, offsetMs ->
                        status = "Speech → text (part ${idx + 1})..."
                        val part = WhisperClient.transcribe(endpoint.trim(), apiKey.trim(), model.trim(), file, spokenLang)
                        all += part.map { it.copy(start = it.start + offsetMs, end = it.end + offsetMs) }
                        file.delete()
                    }
                }
                original = all
                cues = all
                if (all.isEmpty()) throw IllegalStateException("Kono speech paoya jay nai")

                if (translateEnabled) doTranslate(all)
                progress = 1f
                status = "Done: ${cues.size} lines"
            } catch (e: CancellationException) {
                status = "Cancelled"
                throw e
            } catch (e: Exception) {
                errorMsg = e.message ?: e.toString()
            } finally {
                busy = false
                ctx.cacheDir.listFiles { f -> f.name.startsWith("chunk_") }?.forEach { it.delete() }
            }
        }
    }

    fun translateCurrent() {
        saveSettings()
        job = viewModelScope.launch {
            busy = true; errorMsg = null
            try {
                doTranslate(original.ifEmpty { cues })
                status = "Translated: ${cues.size} lines"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMsg = e.message ?: e.toString()
            } finally { busy = false }
        }
    }

    private suspend fun doTranslate(src: List<Cue>) {
        status = "Translating (first time model download hobe)..."
        progress = 0.8f
        cues = CueTranslator.translate(src, spokenLang, targetLang) { progress = 0.8f + it * 0.2f }
        translated = true
    }

    private fun baseName(): String {
        val b = videoName.substringBeforeLast('.').ifBlank { "subtitle" }
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
