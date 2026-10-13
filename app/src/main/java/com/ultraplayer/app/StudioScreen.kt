package com.ultraplayer.app

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.nl.translate.TranslateLanguage
import java.util.Locale

private val SPOKEN = listOf(
    "auto" to "Auto detect", "en" to "English", "bn" to "Bengali", "hi" to "Hindi", "ur" to "Urdu",
    "ar" to "Arabic", "es" to "Spanish", "fr" to "French", "de" to "German", "ja" to "Japanese",
    "ko" to "Korean", "zh" to "Chinese", "ru" to "Russian", "tr" to "Turkish", "id" to "Indonesian",
    "it" to "Italian", "pt" to "Portuguese", "th" to "Thai", "vi" to "Vietnamese",
)

@Composable
fun StudioScreen(vm: StudioViewModel, onPlay: (Uri, String, String?) -> Unit) {
    val ctx = LocalContext.current
    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_LONG).show()

    val targets = remember {
        TranslateLanguage.getAllLanguages()
            .map { it to Locale.forLanguageTag(it).getDisplayLanguage(Locale.ENGLISH) }
            .sortedBy { it.second }
    }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) vm.setVideo(u, displayName(ctx, u))
    }
    val subPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) vm.importSubtitle(u, displayName(ctx, u))
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Subtitle Studio", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(
                "Video → speech-to-text → translate → SRT / VTT / ASS export",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            Section("1. Source") {
                Text(
                    vm.videoName.ifBlank { "Kono video select kora nai" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { videoPicker.launch(arrayOf("video/*", "audio/*")) }) { Text("Pick video") }
                    OutlinedButton(onClick = { subPicker.launch(arrayOf("*/*")) }) { Text("Open .srt/.vtt") }
                }
                OutlinedTextField(
                    value = vm.urlText, onValueChange = { vm.urlText = it; if (it.isNotBlank()) vm.videoUri = null },
                    label = { Text("Or online video URL") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            Section("2. Speech recognition") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = vm.provider == "groq",
                        onClick = { vm.selectProvider("groq") },
                        label = { Text("Groq") },
                    )
                    FilterChip(
                        selected = vm.provider == "gemini",
                        onClick = { vm.selectProvider("gemini") },
                        label = { Text("Gemini") },
                    )
                    FilterChip(
                        selected = vm.provider == "device",
                        onClick = { vm.selectProvider("device") },
                        label = { Text("On-device") },
                    )
                }
                if (vm.provider != "device") {
                    OutlinedTextField(
                        value = if (vm.provider == "gemini") vm.geminiKey else vm.groqKey,
                        onValueChange = { if (vm.provider == "gemini") vm.geminiKey = it else vm.groqKey = it },
                        label = { Text(if (vm.provider == "gemini") "Gemini API key" else "Groq API key") },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (vm.provider == "groq") {
                        OutlinedTextField(
                            value = vm.model, onValueChange = { vm.model = it }, label = { Text("Model") },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    Text("Downloads the Whisper tiny model (~78 MB) once, then transcribes offline without an API key.")
                }
                if (vm.provider == "gemini") {
                    Text(
                        "Gemini 3.8 Flash transcribes uploaded audio chunks and returns timestamped subtitle segments.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Picker("Spoken language", SPOKEN, vm.spokenLang) { vm.spokenLang = it }
            }
        }

        item {
            Section("3. Context-aware Bengali translation") {
                Text("Required for generated subtitles")
                Picker("Translate to", targets, vm.targetLang) { vm.targetLang = it }
                Text(
                    "Nearby dialogue is considered so the Bengali translation keeps the video's meaning and tone. " +
                        "A Gemini API key is required; Gemini transcription reuses its key.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (vm.provider != "gemini") {
                    OutlinedTextField(
                        value = vm.translationGeminiKey,
                        onValueChange = { vm.translationGeminiKey = it },
                        label = { Text("Gemini API key for required translation") },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (vm.cues.isNotEmpty()) {
                    OutlinedButton(onClick = { vm.translateCurrent() }, enabled = !vm.busy) {
                        Text("Translate current subtitle")
                    }
                }
            }
        }

        item {
            Section("4. Generate") {
                if (vm.busy) {
                    LinearProgressIndicator(progress = { vm.progress }, modifier = Modifier.fillMaxWidth())
                    Text(vm.status, fontSize = 13.sp)
                    OutlinedButton(onClick = { vm.cancel() }) { Text("Cancel") }
                } else {
                    Button(onClick = { vm.generate() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Generate subtitles")
                    }
                    if (vm.status.isNotBlank()) Text(vm.status, fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
                }
                vm.errorMsg?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            }
        }

        if (vm.cues.isNotEmpty()) {
            item {
                Section("5. Export (${vm.cues.size} lines)") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("srt", "vtt", "ass", "txt").forEach { f ->
                            OutlinedButton(
                                onClick = { runCatching { vm.export(f) }.onSuccess { toast(it) }.onFailure { toast(it.message ?: "Error") } },
                                contentPadding = PaddingValues(horizontal = 12.dp),
                            ) { Text(f.uppercase()) }
                        }
                    }
                    val playUri = vm.videoUri ?: vm.urlText.trim().takeIf { it.startsWith("http") }?.let { Uri.parse(it) }
                    if (playUri != null) {
                        Button(
                            onClick = { onPlay(playUri, vm.videoName.ifBlank { "Video" }, vm.cacheSrt()) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Play with this subtitle") }
                    }
                }
            }
            items(vm.cues.take(300)) { c ->
                Column {
                    Text(
                        "${fmtTime(c.start)} → ${fmtTime(c.end)}",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.primary,
                    )
                    Text(c.text, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun Picker(label: String, options: List<Pair<String, String>>, value: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val shown = options.firstOrNull { it.first == value }?.second ?: value
    Column {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text(shown) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (code, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(code); open = false })
            }
        }
    }
}
