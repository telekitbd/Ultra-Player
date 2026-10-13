package com.ultraplayer.app
import org.videolan.libvlc.interfaces.IMedia

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.io.File
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

private val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f, 4f)
private val SCALES = listOf(
    "Best fit" to MediaPlayer.ScaleType.SURFACE_BEST_FIT,
    "Fill" to MediaPlayer.ScaleType.SURFACE_FILL,
    "Fit screen" to MediaPlayer.ScaleType.SURFACE_FIT_SCREEN,
    "16:9" to MediaPlayer.ScaleType.SURFACE_16_9,
    "4:3" to MediaPlayer.ScaleType.SURFACE_4_3,
    "Original" to MediaPlayer.ScaleType.SURFACE_ORIGINAL,
)

@Composable
fun PlayerScreen(
    uri: Uri,
    title: String,
    initialSubtitle: String?,
    onBack: () -> Unit,
    onStudio: (Uri) -> Unit,
) {
    val ctx = LocalContext.current
    val activity = ctx as Activity
    val view = LocalView.current
    val audioMgr = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    val libVlc = remember {
        LibVLC(ctx, arrayListOf("--network-caching=2000", "--no-drop-late-frames", "--no-skip-frames"))
    }
    val player = remember { MediaPlayer(libVlc) }
    val videoLayout = remember { VLCVideoLayout(ctx).apply { keepScreenOn = true } }
    val pfd = remember { arrayOfNulls<ParcelFileDescriptor>(1) }
    val firstPlay = remember { booleanArrayOf(true) }

    var playing by remember { mutableStateOf(false) }
    var time by remember { mutableLongStateOf(0L) }
    var length by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(1f) }
    var scaleIdx by remember { mutableIntStateOf(0) }
    var showControls by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var showPanel by remember { mutableStateOf(false) }
    var hudIcon by remember { mutableStateOf<ImageVector?>(null) }
    var hudLabel by remember { mutableStateOf<String?>(null) }
    var hudProgress by remember { mutableFloatStateOf(0f) }
    var repeat by remember { mutableStateOf(false) }
    var loopA by remember { mutableLongStateOf(-1L) }
    var loopB by remember { mutableLongStateOf(-1L) }
    var hud by remember { mutableStateOf<String?>(null) }
    var hudStamp by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragPos by remember { mutableFloatStateOf(0f) }
    var subDelay by remember { mutableLongStateOf(0L) }
    var audDelay by remember { mutableLongStateOf(0L) }
    var interaction by remember { mutableIntStateOf(0) }

    fun showHud(t: String) {
        hud = t
        hudIcon = null
        hudLabel = null
        hudStamp++
    }
    fun showLevelHud(label: String, icon: ImageVector, value: Float) {
        hud = "${(value.coerceIn(0f, 1f) * 100).roundToInt()}%"
        hudIcon = icon
        hudLabel = label
        hudProgress = value.coerceIn(0f, 1f)
        hudStamp++
    }
    fun showVolumeHud(level: Float, max: Int) {
        val safeMax = max.coerceAtLeast(1)
        val safeLevel = level.coerceIn(0f, safeMax.toFloat())
        val icon = when {
            safeLevel == 0f -> Icons.Rounded.VolumeOff
            safeLevel < safeMax * 0.5f -> Icons.Rounded.VolumeDown
            else -> Icons.Rounded.VolumeUp
        }
        showLevelHud("Volume", icon, safeLevel / safeMax)
    }
    fun seekBy(d: Long) {
        val target = (player.getTime() + d).coerceIn(0L, if (length > 0) length else Long.MAX_VALUE)
        player.setTime(target)
        time = target
    }
    fun setSpeed(s: Float) { speed = s; player.setRate(s) }
    fun addSub(f: File) { player.addSlave(IMedia.Slave.Type.Subtitle, Uri.fromFile(f), true) }

    val subPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) runCatching {
            val ext = displayName(ctx, u).substringAfterLast('.', "srt")
            val f = copyToCache(ctx, u, "ext_sub_${System.currentTimeMillis()}.$ext")
            addSub(f)
            showHud("Subtitle loaded")
        }
    }

    // Immersive mode + orientation cleanup
    DisposableEffect(Unit) {
        val c = WindowCompat.getInsetsController(activity.window, view)
        c.hide(WindowInsetsCompat.Type.systemBars())
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        onDispose {
            c.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            val lp = activity.window.attributes
            lp.screenBrightness = -1f
            activity.window.attributes = lp
        }
    }

    // Pause when app goes background
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val ob = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_PAUSE) player.pause() }
        lifecycle.addObserver(ob)
        onDispose { lifecycle.removeObserver(ob) }
    }

    // Playback
    DisposableEffect(uri) {
        player.attachViews(videoLayout, null, true, false)
        val media: Media? = try {
            if (uri.scheme == "content") {
                val p = ctx.contentResolver.openFileDescriptor(uri, "r")
                pfd[0] = p
                Media(libVlc, p!!.fileDescriptor)
            } else Media(libVlc, uri)
        } catch (e: Exception) { null }

        player.setEventListener { e ->
            when (e.type) {
                MediaPlayer.Event.Playing -> {
                    playing = true
                    if (firstPlay[0]) {
                        firstPlay[0] = false
                        initialSubtitle?.let { runCatching { addSub(File(it)) } }
                    }
                }
                MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped -> playing = false
                MediaPlayer.Event.EndReached -> {
                    playing = false
                    if (repeat) { player.setTime(0); player.play() }
                }
                MediaPlayer.Event.TimeChanged -> {
                    time = e.timeChanged
                    if (loopA >= 0 && loopB > loopA && e.timeChanged >= loopB) player.setTime(loopA)
                }
                MediaPlayer.Event.LengthChanged -> length = e.lengthChanged
                MediaPlayer.Event.EncounteredError -> failed = true
            }
        }

        if (media != null) {
            media.setHWDecoderEnabled(true, false)
            val resume = Prefs.getResume(ctx, uri.toString())
            if (resume > 5000) media.addOption(":start-time=${resume / 1000}")
            player.setMedia(media)
            media.release()
            player.play()
        } else failed = true

        onDispose {
            val key = uri.toString()
            if (length > 0 && time in 5000 until (length - 3000)) Prefs.setResume(ctx, key, time)
            else Prefs.clearResume(ctx, key)
            player.setEventListener(null)
            player.stop()
            player.detachViews()
            player.release()
            libVlc.release()
            runCatching { pfd[0]?.close() }
        }
    }

    // Auto-hide controls
    LaunchedEffect(showControls, playing, showPanel, interaction) {
        if (showControls && playing && !showPanel) {
            delay(3500)
            showControls = false
        }
    }
    LaunchedEffect(hudStamp) { delay(if (hudLabel != null) 2500 else 900); hud = null }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { videoLayout }, modifier = Modifier.fillMaxSize())

        // Gesture layer
        var boost by remember { mutableStateOf(false) }
        var speedBefore by remember { mutableFloatStateOf(1f) }
        var dragLeft by remember { mutableStateOf(true) }
        var volAcc by remember { mutableFloatStateOf(0f) }
        Box(
            Modifier.fillMaxSize()
                .pointerInput(locked) {
                    detectTapGestures(
                        onTap = { showControls = !showControls },
                        onDoubleTap = { off ->
                            if (!locked) {
                                if (off.x < size.width / 2) { seekBy(-10_000); showHud("⏪ -10s") }
                                else { seekBy(10_000); showHud("+10s ⏩") }
                            }
                        },
                        onLongPress = {
                            if (!locked) { boost = true; speedBefore = speed; setSpeed(2f); showHud("2x") }
                        },
                        onPress = {
                            tryAwaitRelease()
                            if (boost) { boost = false; setSpeed(speedBefore) }
                        },
                    )
                }
                .pointerInput(locked) {
                    if (!locked) {
                        detectVerticalDragGestures(
                            onDragStart = { off ->
                                dragLeft = off.x < size.width / 2
                                volAcc = audioMgr.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                            },
                        ) { _, dy ->
                            val frac = dy / size.height
                            if (dragLeft) {
                                val lp = activity.window.attributes
                                val cur = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
                                lp.screenBrightness = (cur - frac * 1.5f).coerceIn(0.01f, 1f)
                                activity.window.attributes = lp
                                showLevelHud("Brightness", Icons.Rounded.Brightness6, lp.screenBrightness)
                            } else {
                                val max = audioMgr.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                if (max > 0) {
                                    volAcc = (volAcc - frac * max * 1.5f).coerceIn(0f, max.toFloat())
                                    audioMgr.setStreamVolume(AudioManager.STREAM_MUSIC, volAcc.roundToInt(), 0)
                                    volAcc = audioMgr.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                                    showVolumeHud(volAcc, max)
                                }
                            }
                        }
                    }
                }
        )

        // HUD
        hud?.let {
            if (hudLabel != null && hudIcon != null) {
                Column(
                    Modifier
                        .align(if (hudLabel == "Brightness") Alignment.CenterStart else Alignment.CenterEnd)
                        .padding(horizontal = 20.dp)
                        .width(92.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xE61B1B1F))
                        .padding(horizontal = 12.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(hudIcon!!, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Text(
                        hudLabel!!, color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    VerticalLevelControl(
                        value = hudProgress,
                        onValueChange = { value ->
                            if (hudLabel == "Brightness") {
                                val lp = activity.window.attributes
                                lp.screenBrightness = value.coerceIn(0.01f, 1f)
                                activity.window.attributes = lp
                                showLevelHud("Brightness", Icons.Rounded.Brightness6, lp.screenBrightness)
                            } else {
                                val max = audioMgr.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                if (max > 0) {
                                    audioMgr.setStreamVolume(
                                        AudioManager.STREAM_MUSIC,
                                        (value * max).roundToInt(),
                                        0,
                                    )
                                    showVolumeHud(
                                        audioMgr.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat(),
                                        max,
                                    )
                                }
                            }
                        },
                    )
                    Text(it, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Box(
                    Modifier.align(Alignment.Center).widthIn(max = 280.dp).clip(RoundedCornerShape(14.dp))
                        .background(Color(0xDD1B1B1F)).padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Text(
                        it, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }

        }

        if (failed) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Video play kora gelo na", color = Color.White)
                TextButton(onClick = onBack) { Text("Back") }
            }
        }

        // Locked state
        if (locked && showControls) {
            IconButton(
                onClick = { locked = false; showHud("Unlocked") },
                modifier = Modifier.align(Alignment.CenterStart).padding(16.dp),
            ) { Icon(Icons.Rounded.Lock, null, tint = Color.White) }
        }

        // Controls
        AnimatedVisibility(
            visible = showControls && !locked,
            enter = fadeIn(), exit = fadeOut(),
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(
                    Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, null, tint = Color.White) }
                    Text(
                        title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold,
                    )
                    IconButton(onClick = { onStudio(uri) }) { Icon(Icons.Rounded.Translate, null, tint = Color.White) }
                    IconButton(onClick = { showPanel = true }) { Icon(Icons.Rounded.Tune, null, tint = Color.White) }
                }

                Row(
                    Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { seekBy(-10_000); interaction++ }) {
                        Icon(Icons.Rounded.Replay10, null, tint = Color.White, modifier = Modifier.padding(4.dp))
                    }
                    IconButton(
                        onClick = { if (playing) player.pause() else player.play(); interaction++ },
                        modifier = Modifier.padding(horizontal = 24.dp),
                    ) {
                        Icon(
                            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null,
                            tint = Color.White, modifier = Modifier.padding(2.dp),
                        )
                    }
                    IconButton(onClick = { seekBy(10_000); interaction++ }) {
                        Icon(Icons.Rounded.Forward10, null, tint = Color.White, modifier = Modifier.padding(4.dp))
                    }
                }

                Column(
                    Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(fmtTime(if (dragging) dragPos.toLong() else time), color = Color.White, fontSize = 12.sp)
                        Slider(
                            value = if (dragging) dragPos else time.toFloat(),
                            onValueChange = { dragging = true; dragPos = it; interaction++ },
                            onValueChangeFinished = { player.setTime(dragPos.toLong()); time = dragPos.toLong(); dragging = false },
                            valueRange = 0f..maxOf(length, 1L).toFloat(),
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        )
                        Text(fmtTime(length), color = Color.White, fontSize = 12.sp)
                    }
                    Row(
                        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { locked = true; showControls = false; showHud("Locked") }) {
                            Icon(Icons.Rounded.LockOpen, null, tint = Color.White)
                        }
                        TextButton(onClick = {
                            val i = (SPEEDS.indexOf(speed) + 1) % SPEEDS.size
                            setSpeed(SPEEDS[i]); showHud("${SPEEDS[i]}x"); interaction++
                        }) { Text("${speed}x", color = Color.White, fontWeight = FontWeight.Bold) }
                        IconButton(onClick = {
                            scaleIdx = (scaleIdx + 1) % SCALES.size
                            player.setVideoScale(SCALES[scaleIdx].second)
                            showHud(SCALES[scaleIdx].first); interaction++
                        }) { Icon(Icons.Rounded.AspectRatio, null, tint = Color.White) }
                        IconButton(onClick = { subPicker.launch(arrayOf("*/*")) }) {
                            Icon(Icons.Rounded.Subtitles, null, tint = Color.White)
                        }
                    }
                }
            }
        }
    }

    if (showPanel) {
        val audioTracks = remember { player.getAudioTracks()?.toList() ?: emptyList() }
        val spuTracks = remember { player.getSpuTracks()?.toList() ?: emptyList() }
        var curAudio by remember { mutableIntStateOf(player.getAudioTrack()) }
        var curSpu by remember { mutableIntStateOf(player.getSpuTrack()) }
        Dialog(onDismissRequest = { showPanel = false }) {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                Column(
                    Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Playback", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(SPEEDS) { s ->
                            FilterChip(selected = speed == s, onClick = { setSpeed(s) }, label = { Text("${s}x") })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = repeat, onClick = { repeat = !repeat }, label = { Text("Repeat") })
                        FilterChip(
                            selected = loopA >= 0,
                            onClick = {
                                when {
                                    loopA < 0 -> loopA = time
                                    loopB < 0 -> loopB = time
                                    else -> { loopA = -1; loopB = -1 }
                                }
                            },
                            label = {
                                Text(
                                    when {
                                        loopA < 0 -> "A-B loop"
                                        loopB < 0 -> "A=${fmtTime(loopA)} → set B"
                                        else -> "${fmtTime(loopA)}–${fmtTime(loopB)} ✕"
                                    }
                                )
                            },
                        )
                    }

                    Text("Audio track", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                    if (audioTracks.isEmpty()) Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    audioTracks.forEach { t ->
                        TrackRow(t.name, t.id == curAudio) { player.setAudioTrack(t.id); curAudio = t.id }
                    }

                    Text("Subtitle", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                    spuTracks.forEach { t ->
                        TrackRow(t.name, t.id == curSpu) { player.setSpuTrack(t.id); curSpu = t.id }
                    }
                    OutlinedButton(onClick = { subPicker.launch(arrayOf("*/*")) }) { Text("Load subtitle file…") }

                    Text("Sync", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                    DelayRow("Subtitle", subDelay) { subDelay = it; player.setSpuDelay(it * 1000) }
                    DelayRow("Audio", audDelay) { audDelay = it; player.setAudioDelay(it * 1000) }

                    Button(onClick = { showPanel = false }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(name: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (selected) "● $name" else "○ $name",
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DelayRow(label: String, ms: Long, onChange: (Long) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label: ${if (ms >= 0) "+" else ""}${ms}ms", modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { onChange(ms - 100) }) { Text("-0.1s") }
        OutlinedButton(onClick = { onChange(ms + 100) }, modifier = Modifier.padding(start = 6.dp)) { Text("+0.1s") }
    }
}

@Composable
private fun VerticalLevelControl(value: Float, onValueChange: (Float) -> Unit) {
    val currentValue by rememberUpdatedState(onValueChange)
    Canvas(
        Modifier.width(44.dp).height(160.dp).pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                currentValue((1f - down.position.y / size.height).coerceIn(0f, 1f))
                var pointer = down
                do {
                    val event = awaitPointerEvent()
                    pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (pointer.pressed) {
                        currentValue((1f - pointer.position.y / size.height).coerceIn(0f, 1f))
                        pointer.consume()
                    }
                } while (pointer.pressed)
            }
        },
    ) {
        val centerX = size.width / 2
        val trackWidth = 5.dp.toPx()
        val thumbY = size.height * (1f - value.coerceIn(0f, 1f))
        drawRoundRect(
            color = Color.White.copy(alpha = 0.28f),
            topLeft = Offset(centerX - trackWidth / 2, 0f),
            size = Size(trackWidth, size.height),
            cornerRadius = CornerRadius(trackWidth / 2),
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(centerX - trackWidth / 2, thumbY),
            size = Size(trackWidth, size.height - thumbY),
            cornerRadius = CornerRadius(trackWidth / 2),
        )
        drawCircle(Color.White, radius = 8.dp.toPx(), center = Offset(centerX, thumbY))
    }
}
