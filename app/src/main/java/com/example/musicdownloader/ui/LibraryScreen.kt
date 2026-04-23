package com.example.musicdownloader.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicdownloader.MusicViewModel
import com.example.musicdownloader.VideoItem
import com.example.musicdownloader.data.Song
import com.example.musicdownloader.utils.HapticUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: MusicViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    snackbarHostState: SnackbarHostState,
    onNavigateToPlaylists: () -> Unit,
    onNavigateToLiked: () -> Unit,
    onNavigateToArtists: () -> Unit
) {
    val songs by viewModel.librarySongs.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val initializingDownloads by viewModel.initializingDownloads.collectAsStateWithLifecycle()
    val filterDownloadedOnly by viewModel.filterDownloadedOnly.collectAsState()
    val cachedStreamIds by viewModel.cachedStreamIds.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // State for Search Bar interaction
    var localSearchQuery by remember { mutableStateOf("") }

    // Manage adding to playlist
    var showAddToPlaylistForSong by remember { mutableStateOf<Song?>(null) }

    // Manage Metadata Editing
    var showEditMetadataForSong by remember { mutableStateOf<Song?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F13))
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Search Bar
        OutlinedTextField(
            value = localSearchQuery,
            onValueChange = { localSearchQuery = it },
            label = { Text("Find in library", color = Color.Gray) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricPurple,
                unfocusedBorderColor = Color.Gray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Navigation Cards Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Liked Songs Card
            NavigationCard(
                title = "Liked",
                icon = Icons.Default.Favorite,
                color = PremiumGold, // Gold for favorites
                modifier = Modifier.weight(1f),
                onClick = onNavigateToLiked
            )

            // Playlists Card
            NavigationCard(
                title = "Playlists",
                icon = Icons.Default.List,
                color = DTechBlue, // Blue for lists
                modifier = Modifier.weight(1f),
                onClick = onNavigateToPlaylists
            )

            // Artists Card
            NavigationCard(
                title = "Artists",
                icon = Icons.Default.Person,
                color = Color.White, // White for artists
                modifier = Modifier.weight(1f),
                onClick = onNavigateToArtists
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("All Songs", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)

            // Downloaded Only Toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Downloaded",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (filterDownloadedOnly) DTechBlue else Color.Gray
                )
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = filterDownloadedOnly,
                    onCheckedChange = { viewModel.toggleFilterDownloadedOnly() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = DTechBlue,
                        checkedTrackColor = DTechBlue.copy(alpha = 0.3f),
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color.DarkGray
                    ),
                    modifier = Modifier.scale(0.8f)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        val filteredSongs = remember(songs, localSearchQuery) {
            if (localSearchQuery.isBlank()) songs else songs.filter {
                it.title.contains(localSearchQuery, ignoreCase = true) ||
                it.artist.contains(localSearchQuery, ignoreCase = true)
            }
        }

        LazyColumn(
            contentPadding = contentPadding,
            modifier = Modifier.weight(1f)
        ) {
            items(items = filteredSongs, key = { it.id }) { song ->
                var showMenu by remember { mutableStateOf(false) }

                SwipeableSongRow(
                    onSwipeToQueue = {
                        val success = viewModel.addToQueue(song)
                        if (success) {
                            scope.launch {
                                snackbarHostState.showSnackbar("Added to Queue")
                            }
                        }
                        success
                    }
                ) {
                     Box(modifier = Modifier.background(Color(0xFF0F0F13))) {
                         val isStream = song.filePath.startsWith("stream://")
                         val baseSubtitle = if (song.album != "Unknown Album") "${song.artist} • ${song.album}" else song.artist
                         val subtitle = if (isStream) "$baseSubtitle • Stream" else baseSubtitle
                         val downloadStatus = downloadProgress[song.id]
                         val isInit = initializingDownloads.contains(song.id)

                         MusicRowItem(
                            title = song.title,
                            subtitle = subtitle,
                            thumbnailUrl = song.thumbnailUrl,
                            isLibrary = true,
                            isDownloaded = !isStream,
                            downloadProgress = downloadStatus?.progress,
                            isWaiting = isInit,
                            isCached = cachedStreamIds.contains(song.id),
                            onClick = {
                                viewModel.playSong(song.id, song.title, song.artist, song.thumbnailUrl)
                            },
                            onDownloadClick = {
                                val videoItem = VideoItem(
                                    id = song.id,
                                    title = song.title,
                                    uploader = song.artist,
                                    duration = song.duration,
                                    thumbnailUrl = song.thumbnailUrl,
                                    webUrl = "https://youtube.com/watch?v=${song.id}"
                                )
                                viewModel.downloadSong(videoItem)
                            },
                            onOptionClick = { showMenu = true }
                        )

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(Color(0xFF1C1C26))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Add to Playlist", color = Color.White) },
                                onClick = {
                                    showMenu = false
                                    showAddToPlaylistForSong = song
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Edit Metadata", color = Color.White) },
                                onClick = {
                                    showMenu = false
                                    showEditMetadataForSong = song
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = Color.Red) },
                                onClick = {
                                    showMenu = false
                                    viewModel.deleteSong(song)
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Deleted ${song.title}",
                                            actionLabel = "Undo",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.restoreSong(song)
                                        } else if (result == SnackbarResult.Dismissed) {
                                            viewModel.finalizeDelete(song)
                                        }
                                    }
                                }
                            )
                        }
                     }
                }
            }
        }
    }

    if (showAddToPlaylistForSong != null) {
        val song = showAddToPlaylistForSong!!
        AddToPlaylistSheet(
            playlists = playlists,
            songs = listOf(song),
            onDismiss = { showAddToPlaylistForSong = null },
            onCreatePlaylist = { name -> viewModel.createPlaylist(name) },
            onAddToPlaylist = { playlist, _ ->
                val videoItem = VideoItem(
                    id = song.id,
                    title = song.title,
                    uploader = song.artist,
                    duration = song.duration,
                    thumbnailUrl = song.thumbnailUrl,
                    webUrl = "https://youtube.com/watch?v=${song.id}"
                )
                viewModel.addSongToPlaylist(playlist.id.toInt(), videoItem)
                scope.launch { snackbarHostState.showSnackbar("Added to ${playlist.name}") }
            }
        )
    }

    if (showEditMetadataForSong != null) {
        val song = showEditMetadataForSong!!
        EditMetadataDialog(
            song = song,
            onDismiss = { showEditMetadataForSong = null },
            onSave = { title, artist, album ->
                viewModel.updateSongMetadata(song, title, artist, album)
                showEditMetadataForSong = null
            }
        )
    }
}

@Composable
fun NavigationCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(80.dp)
            .clickable(onClick = onClick),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C26))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.align(Alignment.TopStart).size(24.dp)
            )
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }
    }
}
