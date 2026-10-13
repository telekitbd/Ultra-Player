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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.nl.translate.TranslateLanguage
import java.util.Locale

private val SOURCE_LANGUAGES = listOf(
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

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.setVideo(uri, displayName(ctx, uri))
    }
    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importSubtitle(uri, displayName(ctx, uri))
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Subtitle Translator", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(
                "Open an SRT/VTT subtitle, translate it on-device, then export or play it with a video.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            Section("1. Open subtitle") {
                Text(
                    vm.subtitleName.ifBlank { "No subtitle file selected" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { subtitlePicker.launch(arrayOf("application/x-subrip", "text/vtt", "text/*", "*/*")) },
                ) {
                    Text("Open SRT / VTT subtitle")
                }
                Text(
                    "Translation uses subtitle text; videos without a subtitle file cannot be translated here.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Section("2. Translation") {
                Picker("Subtitle language", SOURCE_LANGUAGES, vm.sourceLang) { vm.sourceLang = it }
                Picker("Translate to", targets, vm.targetLang) { vm.targetLang = it }
                Text(
                    "Google ML Kit translates on this device. No API key or charge is needed; " +
                        "the language model downloads once and then works offline.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (vm.original.isNotEmpty()) {
                    Button(
                        onClick = { vm.translateSubtitle() },
                        enabled = !vm.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Translate subtitle")
                    }
                }
            }
        }

        item {
            Section("3. Video (optional)") {
                Text(
                    vm.videoName.ifBlank { "No video selected" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { videoPicker.launch(arrayOf("video/*")) }) {
                        Text("Pick video")
                    }
                }
                OutlinedTextField(
                    value = vm.urlText,
                    onValueChange = { vm.urlText = it; if (it.isNotBlank()) vm.videoUri = null },
                    label = { Text("Or video URL for playback") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            Section("4. Translation status") {
                if (vm.busy) {
                    LinearProgressIndicator(progress = { vm.progress }, modifier = Modifier.fillMaxWidth())
                    Text(vm.status, fontSize = 13.sp)
                    OutlinedButton(onClick = { vm.cancel() }) { Text("Cancel") }
                } else if (vm.status.isNotBlank()) {
                    Text(vm.status, fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
                }
                vm.errorMsg?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }
        }

        if (vm.cues.isNotEmpty()) {
            item {
                Section("5. Export (${vm.cues.size} lines)") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("srt", "vtt", "ass", "txt").forEach { format ->
                            OutlinedButton(
                                onClick = {
                                    runCatching { vm.export(format) }
                                        .onSuccess { toast(it) }
                                        .onFailure { toast(it.message ?: "Error") }
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp),
                            ) {
                                Text(format.uppercase())
                            }
                        }
                    }
                    val playUri = vm.videoUri
                        ?: vm.urlText.trim().takeIf { it.startsWith("http") }?.let { Uri.parse(it) }
                    if (playUri != null) {
                        Button(
                            onClick = { onPlay(playUri, vm.videoName.ifBlank { "Video" }, vm.cacheSrt()) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Play with this subtitle")
                        }
                    }
                }
            }
            items(vm.cues.take(300)) { cue ->
                Column {
                    Text(
                        "${fmtTime(cue.start)} → ${fmtTime(cue.end)}",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.primary,
                    )
                    Text(cue.text, fontSize = 14.sp)
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
