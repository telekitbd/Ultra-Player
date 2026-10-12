package com.ultraplayer.app

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject

/** OpenAI-compatible Whisper endpoint client (OpenAI, Groq, self-hosted ...). */
object WhisperClient {
    const val OPENAI_URL = "https://api.openai.com/v1/audio/transcriptions"
    const val OPENAI_MODEL = "whisper-1"
    const val GROQ_URL = "https://api.groq.com/openai/v1/audio/transcriptions"
    const val GROQ_MODEL = "whisper-large-v3-turbo"

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(10, TimeUnit.MINUTES)
        .build()

    fun transcribe(endpoint: String, key: String, model: String, file: File, lang: String): List<Cue> {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody("audio/wav".toMediaType()))
            .addFormDataPart("model", model)
            .addFormDataPart("response_format", "verbose_json")
        if (lang != "auto") body.addFormDataPart("language", lang)

        val req = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $key")
            .post(body.build())
            .build()

        http.newCall(req).execute().use { r ->
            val s = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IOException("API error ${r.code}: ${s.take(300)}")
            val json = JSONObject(s)
            val segs = json.optJSONArray("segments")
            val out = ArrayList<Cue>()
            if (segs != null) {
                for (i in 0 until segs.length()) {
                    val o = segs.getJSONObject(i)
                    val t = o.optString("text").trim()
                    if (t.isNotEmpty()) out += Cue((o.getDouble("start") * 1000).toLong(), (o.getDouble("end") * 1000).toLong(), t)
                }
            } else {
                val t = json.optString("text").trim()
                if (t.isNotEmpty()) out += Cue(0, 5000, t)
            }
            return out
        }
    }
}
