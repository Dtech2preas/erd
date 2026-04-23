package com.example.musicdownloader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicdownloader.MusicViewModel
import com.example.musicdownloader.data.Playlist
import com.example.musicdownloader.data.Song
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    onPlaylistClick: (Playlist) -> Unit // Navigate to Detail
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your Playlists", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F0F13))
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = com.example.musicdownloader.ui.ElectricPurple,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create")
            }
        }
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F13))
            .padding(padding)) {

            if (playlists.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No playlists yet", color = Color.Gray)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp)) {
                    items(playlists) { playlist ->
                        PlaylistRow(
                            playlist = playlist,
                            onClick = { onPlaylistClick(playlist) },
                            onRename = { newName -> viewModel.renamePlaylist(playlist.id, newName) },
                            onDelete = { viewModel.deletePlaylist(playlist.id) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Playlist") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        viewModel.createPlaylist(name)
                        showCreateDialog = false
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun PlaylistRow(
    playlist: Playlist,
    onClick: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C26))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(Color(0xFF2C2C36), MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = playlist.name.take(1).uppercase(),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = playlist.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.Gray)
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(Color(0xFF1C1C26))
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename", color = Color.White) },
                        onClick = {
                            showMenu = false
                            showRenameDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = Color.Red) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }

    if (showRenameDialog) {
        var newName by remember { mutableStateOf(playlist.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Playlist") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        onRename(newName)
                        showRenameDialog = false
                    }
                }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    viewModel: MusicViewModel,
    playlistId: Int,
    playlistName: String,
    onBack: () -> Unit
) {
    // Note: In a real app we'd pass ID and fetch name, or pass obj.
    // For now assuming name passed for UI.

    val songs by viewModel.getSongsForPlaylist(playlistId).collectAsState(initial = emptyList())
    var showAddSongsSheet by remember { mutableStateOf(false) }

    // We need all library songs to add
    val librarySongs by viewModel.librarySongs.collectAsStateWithLifecycle()
    val cachedStreamIds by viewModel.cachedStreamIds.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlistName, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showAddSongsSheet = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Songs", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F0F13))
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                   if (songs.isNotEmpty()) {
                       val list = songs.shuffled()
                       viewModel.playSong(
                           id = list.first().id,
                           title = list.first().title,
                           artist = list.first().artist,
                           thumbnailUrl = list.first().thumbnailUrl,
                           contextQueue = list
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
        Box(modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F13))
            .padding(padding)
        ) {
            LazyColumn {
                items(songs) { song ->
                    val subtitle = if (song.album != "Unknown Album") "${song.artist} • ${song.album}" else song.artist

                    var showSongMenu by remember { mutableStateOf(false) }

                    MusicRowItem(
                        title = song.title,
                        subtitle = subtitle,
                        thumbnailUrl = song.thumbnailUrl,
                        isLibrary = true, // Hide download button
                        isCached = cachedStreamIds.contains(song.id),
                        onClick = {
                            viewModel.playSong(
                                id = song.id,
                                title = song.title,
                                artist = song.artist,
                                thumbnailUrl = song.thumbnailUrl,
                                contextQueue = songs
                            )
                        },
                        onOptionClick = { showSongMenu = true }
                    )

                    DropdownMenu(
                        expanded = showSongMenu,
                        onDismissRequest = { showSongMenu = false },
                        modifier = Modifier.background(Color(0xFF1C1C26))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Remove from Playlist", color = Color.Red) },
                            onClick = {
                                showSongMenu = false
                                viewModel.removeSongFromPlaylist(playlistId, song.id)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddSongsSheet) {
        MultiSelectSongSheet(
            songs = librarySongs,
            onDismiss = { showAddSongsSheet = false },
            onConfirm = { selectedSongs ->
                val playlist = Playlist(id = playlistId, name = playlistName) // Mock obj
                viewModel.addSongToPlaylist(playlist, selectedSongs)
                showAddSongsSheet = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiSelectSongSheet(
    songs: List<Song>,
    onDismiss: () -> Unit,
    onConfirm: (List<Song>) -> Unit
) {
    val selected = remember { mutableStateListOf<Song>() }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF1C1C26)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Add Songs", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Button(onClick = { onConfirm(selected) }) {
                    Text("Add (${selected.size})")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(modifier = Modifier.fillMaxHeight(0.7f)) {
                items(songs) { song ->
                    val isSelected = selected.contains(song)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isSelected) selected.remove(song) else selected.add(song)
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = {
                                if (it) selected.add(song) else selected.remove(song)
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(song.title, color = Color.White, maxLines = 1)
                    }
                }
            }
        }
    }
}
