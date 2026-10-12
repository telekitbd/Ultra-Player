package com.ultraplayer.app

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

object CueTranslator {
    suspend fun translate(
        cues: List<Cue>,
        spoken: String,
        target: String,
        onProgress: (Float) -> Unit,
    ): List<Cue> {
        if (cues.isEmpty()) return cues
        val tgt = TranslateLanguage.fromLanguageTag(target) ?: target

        var src: String? = if (spoken != "auto") TranslateLanguage.fromLanguageTag(spoken) else null
        if (src == null) {
            val sample = cues.take(20).joinToString(" ") { it.text }
            val client = LanguageIdentification.getClient()
            try {
                src = TranslateLanguage.fromLanguageTag(client.identifyLanguage(sample).await())
            } finally {
                client.close()
            }
        }
        if (src == null) throw IllegalStateException("Source language detect kora gelo na, language select koro")
        if (src == tgt) return cues

        val tr = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(src).setTargetLanguage(tgt).build()
        )
        try {
            // Prothombar language model download hoy (internet lagbe), tarpor offline
            tr.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            return cues.mapIndexed { i, c ->
                onProgress((i + 1f) / cues.size)
                c.copy(text = tr.translate(c.text).await())
            }
        } finally {
            tr.close()
        }
    }
}
