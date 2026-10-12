package com.ultraplayer.app

import android.util.Base64
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

object GeminiSpeechClient {
    const val MODEL = "gemini-2.5-flash"
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(2, TimeUnit.MINUTES)
        .readTimeout(2, TimeUnit.MINUTES)
        .build()

    fun transcribe(key: String, model: String, language: String, file: File, offsetMs: Long): List<Cue> {
        val prompt = "Transcribe the attached audio in its original language. " +
            (if (language == "auto") "Detect the spoken language." else "The language is $language.") +
            " Return only JSON {\"segments\":[{\"start\":0.0,\"end\":1.0,\"text\":\"words\"}]}. " +
            "Times are seconds relative to this audio. Use short subtitle segments, no translation or markdown."
        val parts = JSONArray()
            .put(JSONObject().put("text", prompt))
            .put(JSONObject().put("inline_data", JSONObject()
                .put("mime_type", "audio/wav")
                .put("data", Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)))
            )
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
            .toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/${model.trim()}:generateContent")
            .header("x-goog-api-key", key.trim())
            .post(body)
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Gemini API error ${response.code}: ${raw.take(300)}")
            val text = JSONObject(raw).optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")
                ?: throw IOException("Gemini returned no transcription")
            val segments = JSONObject(text).optJSONArray("segments")
                ?: throw IOException("Gemini returned malformed subtitle JSON")
            return buildList {
                for (index in 0 until segments.length()) {
                    val segment = segments.getJSONObject(index)
                    val spoken = segment.optString("text").trim()
                    if (spoken.isNotEmpty()) {
                        add(Cue(
                            offsetMs + (segment.optDouble("start") * 1000).toLong(),
                            offsetMs + (segment.optDouble("end") * 1000).toLong(),
                            spoken,
                        ))
                    }
                }
            }
        }
    }
}
