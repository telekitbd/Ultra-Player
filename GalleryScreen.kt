package com.ultraplayer.app

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis

@Composable
fun GalleryScreen(
    vm: GalleryViewModel,
    onPlay: (Uri, String) -> Unit,
    onPhoto: (List<GalleryItem>, Int) -> Unit,
) {
    val ctx = LocalContext.current
    val perms = remember {
        if (Build.VERSION.SDK_INT >= 33)
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_IMAGES)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    var granted by remember {
        mutableStateOf(perms.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED })
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        granted = r.values.all { it }
    }
    LaunchedEffect(granted) {
        if (granted) vm.refresh() else permLauncher.launch(perms)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) onPlay(u, displayName(ctx, u))
    }

    var searching by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var urlDialog by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }

    val list = vm.visible()

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // Header
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (searching) {
                    OutlinedTextField(
                        value = vm.query, onValueChange = { vm.query = it },
                        placeholder = { Text("Search...") }, singleLine = true,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { searching = false; vm.query = "" }) { Icon(Icons.Rounded.Close, null) }
                } else {
                    Column(Modifier.weight(1f)) {
                        Text("UltraPlayer", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "${list.size} items  •  8K ready",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { searching = true }) { Icon(Icons.Rounded.Search, null) }
                    Box {
                        IconButton(onClick = { sortMenu = true }) { Icon(Icons.Rounded.Sort, null) }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            listOf("Newest", "Name", "Size", "Duration").forEachIndexed { i, n ->
                                DropdownMenuItem(
                                    text = { Text(if (vm.sort == i) "✓ $n" else n) },
                                    onClick = { vm.sort = i; sortMenu = false },
                                )
                            }
                        }
                    }
                    IconButton(onClick = { vm.refresh() }) { Icon(Icons.Rounded.Refresh, null) }
                }
            }

            // Videos / Photos pill switch
            Row(
                Modifier.padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                listOf("Videos", "Photos").forEachIndexed { i, n ->
                    val sel = vm.tab == i
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(50))
                            .background(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { vm.tab = i; vm.folder = null }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            n, fontWeight = FontWeight.SemiBold,
                            color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Folder chips
            val folders = vm.folders()
            if (folders.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(selected = vm.folder == null, onClick = { vm.folder = null }, label = { Text("All") })
                    }
                    items(folders) { f ->
                        FilterChip(selected = vm.folder == f, onClick = { vm.folder = f }, label = { Text(f) })
                    }
                }
            }

            when {
                !granted -> Column(
                    Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Media access permission dorkar")
                    Button(onClick = { permLauncher.launch(perms) }, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Allow access")
                    }
                }
                vm.loading && list.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                list.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Kono media paoya jay nai", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(118.dp),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(list, key = { _, it -> it.uri.toString() }) { idx, item ->
                        Tile(item) {
                            if (item.isVideo) onPlay(item.uri, item.name)
                            else {
                                val imgs = list.filter { !it.isVideo }
                                onPhoto(imgs, imgs.indexOf(item).coerceAtLeast(0))
                            }
                        }
                    }
                }
            }
        }

        Column(
            Modifier.align(Alignment.BottomEnd).padding(16.dp),
            horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SmallFloatingActionButton(onClick = { urlDialog = true }) { Icon(Icons.Rounded.Link, "Stream URL") }
            FloatingActionButton(onClick = { picker.launch(arrayOf("video/*", "audio/*", "application/octet-stream")) }) {
                Icon(Icons.Rounded.FolderOpen, "Open file")
            }
        }
    }

    if (urlDialog) {
        AlertDialog(
            onDismissRequest = { urlDialog = false },
            title = { Text("Network stream") },
            text = {
                OutlinedTextField(
                    value = url, onValueChange = { url = it }, singleLine = true,
                    placeholder = { Text("http(s)://, rtsp://, m3u8 ...") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val u = url.trim()
                    if (u.isNotEmpty()) { urlDialog = false; onPlay(Uri.parse(u), u.substringAfterLast('/').ifBlank { "Stream" }) }
                }) { Text("Play") }
            },
            dismissButton = { TextButton(onClick = { urlDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Tile(item: GalleryItem, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val label = if (item.isVideo) resLabel(item.width, item.height) else ""
    Box(
        Modifier.aspectRatio(1f).clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(ctx).data(item.uri).apply {
                if (item.isVideo) videoFrameMillis(1000)
            }.size(360).build(),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (item.isVideo) {
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.padding(end = 2.dp))
                    Text(fmtTime(item.durationMs), color = Color.White, fontSize = 12.sp)
                }
            }
            if (label.isNotEmpty() && label != "SD") {
                val hot = label == "8K" || label == "4K"
                Box(
                    Modifier.align(Alignment.TopStart).padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (hot) MaterialTheme.colorScheme.tertiary else Color(0x99000000))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        label, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = if (hot) Color.Black else Color.White,
                    )
                }
            }
        }
    }
}
