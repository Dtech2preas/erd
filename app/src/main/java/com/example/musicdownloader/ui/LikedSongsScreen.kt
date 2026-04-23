package com.example.musicdownloader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicdownloader.MusicViewModel
import com.example.musicdownloader.data.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LikedSongsScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    onSongClick: (String) -> Unit
) {
    val likedIds by viewModel.likedSongIds.collectAsStateWithLifecycle()
    val allSongs by viewModel.librarySongs.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf("Recently Added") }
    var expandedSortMenu by remember { mutableStateOf(false) }

    // Filter locally for now
    val cachedStreamIds by viewModel.cachedStreamIds.collectAsStateWithLifecycle()
    val likedSongs = remember(likedIds, allSongs, searchQuery, sortOption) {
        var filtered = allSongs.filter { likedIds.contains(it.id) }

        if (searchQuery.isNotBlank()) {
            filtered = filtered.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true)
            }
        }

        when (sortOption) {
            "A-Z" -> filtered.sortedBy { it.title.lowercase() }
            "Artist" -> filtered.sortedBy { it.artist.lowercase() }
            // Assuming default list order or reversed is recent enough for now.
            // LikedIds are often appended at the end. We'll use id index from likedIds.
            "Recently Added" -> filtered.sortedByDescending { likedIds.indexOf(it.id) }
            else -> filtered
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Liked Songs", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    Box {
                        TextButton(onClick = { expandedSortMenu = true }) {
                            Text("Sort: $sortOption", color = DTechBlue)
                        }
                        DropdownMenu(
                            expanded = expandedSortMenu,
                            onDismissRequest = { expandedSortMenu = false },
                            modifier = Modifier.background(Color(0xFF1C1C26))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Recently Added", color = Color.White) },
                                onClick = { sortOption = "Recently Added"; expandedSortMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("A-Z", color = Color.White) },
                                onClick = { sortOption = "A-Z"; expandedSortMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Artist", color = Color.White) },
                                onClick = { sortOption = "Artist"; expandedSortMenu = false }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F0F13))
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                   if (likedSongs.isNotEmpty()) {
                       viewModel.playSong(
                           id = likedSongs.first().id,
                           title = likedSongs.first().title,
                           artist = likedSongs.first().artist,
                           thumbnailUrl = likedSongs.first().thumbnailUrl,
                           contextQueue = likedSongs
                       )
                   }
                },
                containerColor = com.example.musicdownloader.ui.ElectricPurple,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Refresh, "Shuffle") },
                text = { Text("Shuffle All") }
            )
        }
    ) { padding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F13))
            .padding(padding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search liked songs", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricPurple,
                    unfocusedBorderColor = Color.Gray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            if (likedSongs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (searchQuery.isNotBlank()) "No matches found" else "No liked songs yet", color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(likedSongs) { song ->
                         val subtitle = if (song.album != "Unknown Album") "${song.artist} • ${song.album}" else song.artist
                         MusicRowItem(
                             title = song.title,
                             subtitle = subtitle,
                             thumbnailUrl = song.thumbnailUrl,
                             isLibrary = true,
                             isCached = cachedStreamIds.contains(song.id),
                             onClick = {
                                 viewModel.playSong(
                                     id = song.id,
                                     title = song.title,
                                     artist = song.artist,
                                     thumbnailUrl = song.thumbnailUrl,
                                     contextQueue = likedSongs
                                 )
                             }
                         )
                     }
                 }
             }
        }
    }
}
