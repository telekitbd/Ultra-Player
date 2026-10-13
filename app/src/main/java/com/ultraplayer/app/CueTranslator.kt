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
        sourceLanguage: String,
        target: String,
        onProgress: (Float) -> Unit,
    ): List<Cue> {
        if (cues.isEmpty()) return cues
        val tgt = TranslateLanguage.fromLanguageTag(target)
            ?: throw IllegalStateException("Selected target language is not supported for on-device translation")

        val src = if (sourceLanguage != "auto") {
            TranslateLanguage.fromLanguageTag(sourceLanguage)
                ?: throw IllegalStateException("Selected subtitle language is not supported for on-device translation")
        } else {
            val sample = cues.take(20).joinToString(" ") { it.text }
            val client = LanguageIdentification.getClient()
            try {
                TranslateLanguage.fromLanguageTag(client.identifyLanguage(sample).await())
            } finally {
                client.close()
            }
        } ?: throw IllegalStateException(
            "Could not detect the spoken language. Select it manually and try again.",
        )
        if (src == tgt) return cues

        val tr = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(src).setTargetLanguage(tgt).build()
        )
        try {
            try {
                tr.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            } catch (error: Exception) {
                throw IllegalStateException(
                    "Could not download the on-device translation model. Check your internet connection and try again.",
                    error,
                )
            }
            return cues.mapIndexed { i, c ->
                onProgress((i + 1f) / cues.size)
                if (c.text.isBlank()) c else c.copy(text = tr.translate(c.text).await())
            }
        } finally {
            tr.close()
        }
    }
}
