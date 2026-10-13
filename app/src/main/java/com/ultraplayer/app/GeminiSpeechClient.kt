package com.ultraplayer.app

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

object GeminiSpeechClient {
    const val MODEL = "gemini-3.8-flash"
    private const val API_ROOT = "https://generativelanguage.googleapis.com"
    private const val AUDIO_MIME_TYPE = "audio/wav"

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    suspend fun transcribe(
        key: String,
        model: String,
        language: String,
        file: File,
        offsetMs: Long,
    ): List<Cue> = withContext(Dispatchers.IO) {
        if (!file.isFile || file.length() <= 44L) {
            throw IOException("Gemini transcription needs a non-empty WAV audio chunk")
        }
        val apiKey = key.trim()
        if (apiKey.isEmpty()) throw IOException("Gemini API key is missing")

        val audioFile = upload(apiKey, file)
        val durationMs = (file.length() - 44L) * 1000L / 32L
        val languageHint = if (language == "auto") {
            "Detect the spoken language automatically."
        } else {
            "The expected spoken language is $language."
        }
        val prompt = "Transcribe the speech in this audio in its original language. $languageHint " +
            "Return every spoken segment in order with start_time and end_time as seconds from the " +
            "beginning of this audio chunk, and text containing only the words spoken in that segment. " +
            "Use concise subtitle-sized segments, preserve meaning and punctuation, and do not translate, " +
            "summarize, or add commentary. Return an empty segments array if there is no intelligible speech."
        val schema = JSONObject()
            .put("type", "object")
            .put("properties", JSONObject().put("segments", JSONObject()
                .put("type", "array")
                .put("items", JSONObject()
                    .put("type", "object")
                    .put("properties", JSONObject()
                        .put("start_time", JSONObject().put("type", "number"))
                        .put("end_time", JSONObject().put("type", "number"))
                        .put("text", JSONObject().put("type", "string")))
                    .put("required", JSONArray().put("start_time").put("end_time").put("text"))
                    .put("additionalProperties", false)))
            .put("required", JSONArray().put("segments"))
            .put("additionalProperties", false)
        val body = JSONObject()
            .put("model", model.trim().ifBlank { MODEL })
            .put("input", JSONArray()
                .put(JSONObject().put("type", "text").put("text", prompt))
                .put(JSONObject()
                    .put("type", "audio")
                    .put("uri", audioFile.optString("uri"))
                    .put("mime_type", audioFile.optString("mime_type", AUDIO_MIME_TYPE))))
            .put("response_format", schema)
            .toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("$API_ROOT/v1beta/interactions")
            .header("x-goog-api-key", apiKey)
            .post(body)
            .build()

        val raw = http.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Gemini transcription error ${response.code}: ${responseBody.take(400)}")
            }
            responseBody
        }
        val interaction = JSONObject(raw)
        val outputText = interaction.optString("output_text").ifBlank {
            val output = interaction.optJSONArray("outputs") ?: interaction.optJSONArray("output")
            (0 until (output?.length() ?: 0)).mapNotNull { index ->
                output?.optJSONObject(index)?.optString("text")?.takeIf { it.isNotBlank() }
            }.joinToString("\n")
        }
        if (outputText.isBlank()) {
            throw IOException("Gemini returned no transcription output")
        }
        val segments = try {
            JSONObject(outputText).getJSONArray("segments")
        } catch (error: Exception) {
            throw IOException("Gemini returned malformed timestamped transcription", error)
        }
        buildList {
            for (index in 0 until segments.length()) {
                val segment = segments.optJSONObject(index)
                    ?: throw IOException("Gemini returned an invalid transcription segment")
                val text = segment.optString("text").trim()
                val startMs = (segment.optDouble("start_time", -1.0) * 1000).toLong()
                    .coerceIn(0L, durationMs)
                val endMs = (segment.optDouble("end_time", -1.0) * 1000).toLong()
                    .coerceIn(startMs, durationMs)
                if (text.isNotEmpty() && endMs > startMs) {
                    add(Cue(offsetMs + startMs, offsetMs + endMs, text))
                }
            }
        }.sortedBy { it.start }
    }

    private fun upload(key: String, file: File): JSONObject {
        val startRequest = Request.Builder()
            .url("$API_ROOT/upload/v1beta/files")
            .header("x-goog-api-key", key)
            .header("X-Goog-Upload-Protocol", "resumable")
            .header("X-Goog-Upload-Command", "start")
            .header("X-Goog-Upload-Header-Content-Length", file.length().toString())
            .header("X-Goog-Upload-Header-Content-Type", AUDIO_MIME_TYPE)
            .post(JSONObject().put("file", JSONObject().put("display_name", file.name))
                .toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        val uploadUrl = http.newCall(startRequest).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Gemini audio upload error ${response.code}: ${responseBody.take(400)}")
            }
            response.header("X-Goog-Upload-URL")
                ?: throw IOException("Gemini audio upload did not return an upload URL")
        }
        val uploadRequest = Request.Builder()
            .url(uploadUrl)
            .header("x-goog-api-key", key)
            .header("X-Goog-Upload-Offset", "0")
            .header("X-Goog-Upload-Command", "upload, finalize")
            .post(file.asRequestBody(AUDIO_MIME_TYPE.toMediaType()))
            .build()
        return http.newCall(uploadRequest).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Gemini audio upload error ${response.code}: ${responseBody.take(400)}")
            }
            val uploaded = JSONObject(responseBody).optJSONObject("file")
                ?: throw IOException("Gemini audio upload returned no file metadata")
            if (uploaded.optString("uri").isBlank()) {
                throw IOException("Gemini audio upload returned no file URI")
            }
            uploaded
        }
    }
}
