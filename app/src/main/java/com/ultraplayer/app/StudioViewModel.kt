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

    var provider by mutableStateOf(sp.getString("provider", "groq") ?: "groq")
        private set
    var groqKey by mutableStateOf(sp.getString(
        "groq_key",
        if (sp.getString("endpoint", WhisperClient.GROQ_URL) == WhisperClient.GROQ_URL)
            sp.getString("key", "") else "",
    ) ?: "")
    var geminiKey by mutableStateOf(sp.getString("gemini_key", "") ?: "")
    var endpoint by mutableStateOf(sp.getString("endpoint", WhisperClient.GROQ_URL) ?: WhisperClient.GROQ_URL)
    var model by mutableStateOf(
        sp.getString("model", null).orEmpty()
            .takeUnless { it.isBlank() || it == "whisper-1" }
            ?: if (provider == "gemini") GeminiSpeechClient.MODEL
            else if (provider == "groq") WhisperClient.GROQ_MODEL else ""
    )
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
        sp.edit().putString("provider", provider).putString("groq_key", groqKey)
            .putString("gemini_key", geminiKey).putString("key", groqKey)
            .putString("endpoint", endpoint).putString("model", model)
            .putString("spoken", spokenLang).putString("target", targetLang).apply()
    }

    fun selectProvider(value: String) {
        if (provider == value) return
        provider = value
        when (value) {
            "groq" -> {
                endpoint = WhisperClient.GROQ_URL
                model = WhisperClient.GROQ_MODEL
            }
            "gemini" -> model = GeminiSpeechClient.MODEL
            else -> model = ""
        }
        saveSettings()
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
                if (provider != "device" &&
                    (if (provider == "gemini") geminiKey else groqKey).isBlank()) {
                    throw IllegalStateException("Selected provider API key dao")
                }

                val all = ArrayList<Cue>()
                val localModel = if (provider == "device") {
                    status = "Offline Whisper model prepare hocche..."
                    LocalWhisper.modelFile(ctx) { percent ->
                        withContext(Dispatchers.Main) { status = "Whisper model download: $percent%" }
                    }
                } else null
                withContext(Dispatchers.IO) {
                    status = "Audio extract hocche..."
                    AudioChunker.process(
                        ctx, src, if (provider == "gemini") 45 else if (provider == "device") 30 else 600,
                        onProgress = { progress = it * 0.8f },
                    ) { idx, file, offsetMs ->
                        withContext(Dispatchers.Main) {
                            status = "Speech → text ($provider, part ${idx + 1})..."
                        }
                        val part = when (provider) {
                            "gemini" -> GeminiSpeechClient.transcribe(
                                geminiKey, model.ifBlank { GeminiSpeechClient.MODEL }, spokenLang, file, 0L
                            )
                            "device" -> LocalWhisper.transcribe(
                                file, localModel ?: error("Whisper model missing"), spokenLang, 0L
                            )
                            else -> WhisperClient.transcribe(
                                endpoint.trim(), groqKey.trim(), model.ifBlank { WhisperClient.GROQ_MODEL }, file, spokenLang
                            )
                        }
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
