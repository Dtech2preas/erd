package com.example.musicdownloader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicdownloader.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    artistName: String,
    viewModel: MusicViewModel,
    onBack: () -> Unit
) {
    val allSongs by viewModel.librarySongs.collectAsStateWithLifecycle()

    // Filter for this artist
    val cachedStreamIds by viewModel.cachedStreamIds.collectAsStateWithLifecycle()
    val artistSongs = remember(allSongs, artistName) {
        allSongs.filter { it.artist == artistName }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(artistName, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("${artistSongs.size} Songs", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    // Play All Button
                     IconButton(onClick = {
                         if (artistSongs.isNotEmpty()) {
                              val first = artistSongs.first()
                              // Play the first song, passing the filtered artist list as the context queue
                              viewModel.playSong(first.id, first.title, first.artist, first.thumbnailUrl, artistSongs)
                         }
                     }) {
                         Icon(Icons.Default.PlayArrow, contentDescription = "Play Artist", tint = ElectricPurple)
                     }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F0F13))
            )
        },
        containerColor = Color(0xFF0F0F13)
    ) { padding ->
        LazyColumn(
            contentPadding = padding,
            modifier = Modifier.fillMaxSize()
        ) {
            items(artistSongs) { song ->
                // Keeping original display logic as requested
                val subtitle = if (song.album != "Unknown Album") "${song.artist} • ${song.album}" else song.artist
                MusicRowItem(
                    title = song.title,
                    subtitle = subtitle,
                    thumbnailUrl = song.thumbnailUrl,
                    isLibrary = true,
                    isCached = cachedStreamIds.contains(song.id),
                    onClick = {
                         // Play this song, within the context of the Artist
                         viewModel.playSong(song.id, song.title, song.artist, song.thumbnailUrl, artistSongs)
                    }
                )
            }
        }
    }
}
