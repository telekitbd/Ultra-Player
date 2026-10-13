package com.ultraplayer.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

sealed interface Screen {
    object Home : Screen
    data class Player(val uri: Uri, val title: String, val subtitle: String? = null) : Screen
    data class Viewer(val items: List<GalleryItem>, val index: Int) : Screen
}

class MainActivity : ComponentActivity() {
    private var screen by mutableStateOf<Screen>(Screen.Home)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        setContent { UltraTheme { Root() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        val d = i?.data ?: return
        if (i.action == Intent.ACTION_VIEW) screen = Screen.Player(d, displayName(this, d))
    }

    @Composable
    private fun Root() {
        val gvm: GalleryViewModel = viewModel()
        val svm: StudioViewModel = viewModel()
        var tab by rememberSaveable { mutableIntStateOf(0) }

        BackHandler(screen !is Screen.Home) { screen = Screen.Home }

        when (val s = screen) {
            Screen.Home -> Scaffold(
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            selected = tab == 0, onClick = { tab = 0 },
                            icon = { Icon(Icons.Rounded.VideoLibrary, null) },
                            label = { Text("Gallery") },
                        )
                        NavigationBarItem(
                            selected = tab == 1, onClick = { tab = 1 },
                            icon = { Icon(Icons.Rounded.Subtitles, null) },
                            label = { Text("Subtitle Translator") },
                        )
                    }
                },
            ) { pad ->
                Box(Modifier.padding(pad)) {
                    if (tab == 0) {
                        GalleryScreen(
                            vm = gvm,
                            onPlay = { uri, name -> screen = Screen.Player(uri, name) },
                            onPhoto = { list, idx -> screen = Screen.Viewer(list, idx) },
                        )
                    } else {
                        StudioScreen(svm) { uri, name, sub -> screen = Screen.Player(uri, name, sub) }
                    }
                }
            }

            is Screen.Player -> PlayerScreen(
                uri = s.uri,
                title = s.title,
                initialSubtitle = s.subtitle,
                onBack = { screen = Screen.Home },
                onStudio = { uri ->
                    svm.setVideo(uri, displayName(this@MainActivity, uri))
                    tab = 1
                    screen = Screen.Home
                },
            )

            is Screen.Viewer -> PhotoViewer(s.items, s.index) { screen = Screen.Home }
        }
    }
}
