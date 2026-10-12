package com.ultraplayer.app

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

object GeminiTranslationClient {
    private const val MODEL = "gemini-2.5-flash"
    private const val BATCH_SIZE = 24

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun translate(
        key: String,
        cues: List<Cue>,
        sourceLanguage: String,
        targetLanguage: String,
        onProgress: (Float) -> Unit,
    ): List<Cue> = withContext(Dispatchers.IO) {
        if (cues.isEmpty()) return@withContext cues
        val translated = cues.toMutableList()
        val batches = cues.indices step BATCH_SIZE
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

        batches.forEachIndexed { batchIndex, start ->
            val end = minOf(start + BATCH_SIZE, cues.size)
            val context = JSONArray()
            for (index in maxOf(0, start - 2) until minOf(cues.size, end + 2)) {
                context.put(JSONObject()
                    .put("id", index)
                    .put("text", cues[index].text)
                    .put("translate", index in start until end))
            }
            val source = if (sourceLanguage == "auto") "automatically detected from the dialogue" else sourceLanguage
            val target = if (targetLanguage == "bn" || targetLanguage.startsWith("bn-")) {
                "natural, conversational Bangladeshi Bengali (বাংলাদেশে প্রচলিত স্বাভাবিক বাংলা; সহজে বোঝা যায় এমন কথ্য ভাষা)"
            } else {
                targetLanguage
            }
            val prompt = "Translate the selected subtitle lines from $source into $target. Treat all lines as dialogue from the same " +
                "video and use neighboring lines only to understand context. Preserve the speaker's meaning, tone, " +
                "idioms, names, and relationships; do not translate word-for-word. Keep each subtitle concise and " +
                "easy to understand. Return every selected id exactly once, with no extra text or explanations, " +
                "as JSON: {\"translations\":[{\"id\":0,\"text\":\"...\"}]}. Do not change ids or include " +
                "unselected context lines in the translations array. Subtitle context: $context"
            val body = JSONObject()
                .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray()
                    .put(JSONObject().put("text", prompt)))))
                .put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
                .toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .header("x-goog-api-key", key.trim())
                .post(body)
                .build()
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("Gemini translation error ${response.code}: ${raw.take(300)}")
                }
                val text = JSONObject(raw).optJSONArray("candidates")?.optJSONObject(0)
                    ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")
                    ?: throw IOException("Gemini returned no translation")
                val rows = JSONObject(text).optJSONArray("translations")
                    ?: throw IOException("Gemini returned malformed translation JSON")
                val byId = HashMap<Int, String>(rows.length())
                for (rowIndex in 0 until rows.length()) {
                    val row = rows.getJSONObject(rowIndex)
                    val id = row.optInt("id", -1)
                    val value = row.optString("text").trim()
                    if (id !in start until end || value.isEmpty() || byId.put(id, value) != null) {
                        throw IOException("Gemini returned invalid translation entries")
                    }
                }
                for (index in start until end) {
                    val value = byId[index] ?: throw IOException("Gemini omitted subtitle line ${index + 1}")
                    translated[index] = cues[index].copy(text = value)
                }
            }
            onProgress((batchIndex + 1f) / batches.count())
        }
        translated
    }
}
