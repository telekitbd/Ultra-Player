package com.ultraplayer.app

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object LocalWhisper {
    private const val MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin"
    private const val MODEL_NAME = "ggml-tiny.bin"
    private const val SAMPLE_RATE = 16000
    private const val WINDOW_SAMPLES = SAMPLE_RATE * 30
    private val http = OkHttpClient.Builder().readTimeout(5, TimeUnit.MINUTES).build()
    private val mutex = Mutex()
    private var modelPath: String? = null
    private var handle = 0L

    init { System.loadLibrary("ultra_whisper") }

    private external fun nativeInit(path: String): Long
    private external fun nativeTranscribe(handle: Long, audio: FloatArray, language: String): Array<String>?
    private external fun nativeFree(handle: Long)

    suspend fun modelFile(context: Context, onProgress: suspend (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            val model = File(context.filesDir, MODEL_NAME)
            if (model.exists() && model.length() > 1_000_000) return@withContext model
            val temp = File(context.filesDir, "$MODEL_NAME.download")
            http.newCall(Request.Builder().url(MODEL_URL).build()).execute().use { response ->
                check(response.isSuccessful) { "Whisper model download failed (HTTP ${response.code})" }
                val body = response.body ?: error("Whisper model download returned no data")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    temp.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var downloaded = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            downloaded += count
                            if (total > 0) onProgress((downloaded * 100 / total).toInt())
                        }
                    }
                }
            }
            check(temp.length() > 1_000_000) { "Downloaded Whisper model is incomplete" }
            if (!temp.renameTo(model)) {
                temp.copyTo(model, overwrite = true)
                temp.delete()
            }
            model
        }

    suspend fun transcribe(wav: File, model: File, language: String, offsetMs: Long): List<Cue> =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                RandomAccessFile(wav, "r").use { input ->
                    require(input.length() >= 44) { "Audio chunk is not a valid WAV file" }
                    input.seek(40)
                    val bytes = Integer.reverseBytes(input.readInt()).toLong()
                        .coerceAtMost(input.length() - 44)
                    require(bytes > 0) { "Audio chunk is empty" }
                    input.seek(44)
                    if (modelPath != model.absolutePath || handle == 0L) {
                        if (handle != 0L) nativeFree(handle)
                        handle = nativeInit(model.absolutePath)
                        check(handle != 0L) { "Could not load the on-device Whisper model" }
                        modelPath = model.absolutePath
                    }
                    val nativeHandle = handle
                    buildList {
                        var consumed = 0L
                        while (consumed * 2 < bytes) {
                            val count = minOf(WINDOW_SAMPLES.toLong(), bytes / 2 - consumed).toInt()
                            val pcm = ByteArray(count * 2)
                            input.readFully(pcm)
                            val samples = FloatArray(count) { i ->
                                val low = pcm[i * 2].toInt() and 0xff
                                val high = pcm[i * 2 + 1].toInt()
                                (((high shl 8) or low).toShort().toInt() / 32768f)
                            }
                            val windowOffset = offsetMs + consumed * 1000 / SAMPLE_RATE
                            nativeTranscribe(nativeHandle, samples, language)?.forEach { line ->
                                val fields = line.split('\t', limit = 3)
                                if (fields.size == 3 && fields[2].isNotBlank()) {
                                    add(Cue(windowOffset + fields[0].toLong(),
                                        windowOffset + fields[1].toLong(), fields[2].trim()))
                                }
                            } ?: error("On-device Whisper transcription failed")
                            consumed += count
                        }
                    }
                }
            }
        }
}
