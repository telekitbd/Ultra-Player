package com.ultraplayer.app

import android.util.Base64
import java.io.File
import java.io.IOException
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

object GeminiSpeechClient {
    const val MODEL = "gemini-3.8-live"
    private const val SAMPLE_RATE = 16_000
    private const val AUDIO_MIME_TYPE = "audio/pcm;rate=16000"
    private const val AUDIO_BYTES_PER_SECOND = SAMPLE_RATE * 2
    private const val CHUNK_BYTES = 3_200

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    suspend fun transcribe(
        key: String,
        model: String,
        language: String,
        file: File,
        offsetMs: Long,
    ): List<Cue> = withContext(Dispatchers.IO) {
        val wav = file.readBytes()
        if (wav.size <= 44 || String(wav, 0, 4, Charsets.US_ASCII) != "RIFF") {
            throw IOException("Gemini Live needs a valid PCM WAV audio chunk")
        }
        val pcm = wav.copyOfRange(44, wav.size)
        val modelName = model.trim().ifBlank { MODEL }
        val setup = JSONObject()
            .put("setup", JSONObject()
                .put("model", "models/$modelName")
                .put("generationConfig", JSONObject()
                    .put("responseModalities", JSONArray().put("AUDIO")))
                .put("inputAudioTranscription", JSONObject())
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(
                    JSONObject().put(
                        "text",
                        "Transcribe the incoming audio exactly in its original spoken language. " +
                            (if (language == "auto") "Detect its language automatically. " else "The spoken language is $language. ") +
                            "Do not summarize, translate, or add commentary.",
                    ),
                ))))
            .toString()

        val setupComplete = CompletableDeferred<Unit>()
        val turnComplete = CompletableDeferred<Unit>()
        val samplesSent = AtomicLong(0)
        val transcripts = Collections.synchronizedList(mutableListOf<Pair<String, Long>>())
        val liveApiUrl =
            "https://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta/" +
                "GenerativeService.BidiGenerateContent"
        val request = Request.Builder()
            .url(liveApiUrl.toHttpUrl().newBuilder().addQueryParameter("key", key.trim()).build())
            .build()

        val socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                if (!webSocket.send(setup)) {
                    val error = IOException("Could not start Gemini Live transcription session")
                    setupComplete.completeExceptionally(error)
                    turnComplete.completeExceptionally(error)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val message = JSONObject(text)
                    message.optJSONObject("error")?.let {
                        val error = IOException("Gemini Live error: ${it.optString("message", it.toString())}")
                        setupComplete.completeExceptionally(error)
                        turnComplete.completeExceptionally(error)
                        return
                    }
                    if (message.has("setupComplete")) setupComplete.complete(Unit)
                    val content = message.optJSONObject("serverContent")
                    val finalText = content?.optJSONObject("inputTranscription")
                        ?.optString("text").orEmpty().trim()
                    if (finalText.isNotEmpty()) {
                        transcripts.add(finalText to samplesSent.get())
                    }
                    if (content?.optBoolean("turnComplete") == true) turnComplete.complete(Unit)
                } catch (error: Exception) {
                    setupComplete.completeExceptionally(error)
                    turnComplete.completeExceptionally(error)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                val error = IOException(
                    "Gemini Live connection failed${response?.let { " (${it.code})" } ?: ""}: ${t.message}",
                    t,
                )
                setupComplete.completeExceptionally(error)
                turnComplete.completeExceptionally(error)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!turnComplete.isCompleted) {
                    val error = IOException("Gemini Live closed before transcription finished: $reason")
                    setupComplete.completeExceptionally(error)
                    turnComplete.completeExceptionally(error)
                }
            }
        })

        try {
            withTimeout(30_000) { setupComplete.await() }
            var position = 0
            while (position < pcm.size) {
                val end = minOf(position + CHUNK_BYTES, pcm.size)
                val packet = pcm.copyOfRange(position, end)
                val message = JSONObject()
                    .put("realtimeInput", JSONObject().put("audio", JSONObject()
                        .put("mimeType", AUDIO_MIME_TYPE)
                        .put("data", Base64.encodeToString(packet, Base64.NO_WRAP))))
                    .toString()
                if (!socket.send(message)) throw IOException("Gemini Live audio stream was rejected")
                val samples = packet.size / 2
                samplesSent.addAndGet(samples.toLong())
                position = end
                delay((samples * 1000L / SAMPLE_RATE).coerceAtLeast(1L))
            }
            if (!socket.send(JSONObject().put("realtimeInput", JSONObject().put("audioStreamEnd", true)).toString())) {
                throw IOException("Could not finish Gemini Live audio stream")
            }
            withTimeout(60_000) { turnComplete.await() }
        } finally {
            socket.close(1000, "transcription complete")
        }

        val durationMs = pcm.size * 1000L / AUDIO_BYTES_PER_SECOND
        var lastEndMs = 0L
        val result = buildList {
            transcripts.toList().forEach { (text, sentSamples) ->
                val estimatedDurationMs = (text.split(Regex("\\s+")).size * 420L).coerceIn(600L, 8_000L)
                val reportedEndMs = sentSamples * 1000L / SAMPLE_RATE
                val endMs = maxOf(reportedEndMs, lastEndMs + estimatedDurationMs).coerceAtMost(durationMs)
                val startMs = maxOf(lastEndMs, endMs - estimatedDurationMs)
                if (text.isNotBlank() && endMs > startMs) {
                    add(Cue(offsetMs + startMs, offsetMs + endMs, text))
                    lastEndMs = endMs
                }
            }
        }
        if (result.isEmpty()) throw IOException("Gemini Live returned no finalized speech transcription")
        result
    }
}
