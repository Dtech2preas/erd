package com.example.musicdownloader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicdownloader.data.Playlist
import com.example.musicdownloader.data.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    playlists: List<Playlist>,
    songs: List<Song>, // Available songs to add (usually passed from Library)
    onDismiss: () -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onAddToPlaylist: (Playlist, List<Song>) -> Unit // Usually just one song, but flexible
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    // In this flow, we usually are adding *a specific song* TO a playlist.
    // Or adding *songs* TO a specific playlist.
    // The prompt says: "Clicking '+' opens a Dialog listing user's playlists -> User selects playlist -> Song added".
    // So this sheet should display Playlists.

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C26)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Add to Playlist",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Create New Item
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showCreateDialog = true }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFF2C2C36), MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", fontSize = 24.sp, color = Color.White)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text("New Playlist", color = Color.White, fontWeight = FontWeight.SemiBold)
            }

            // Playlist List
            LazyColumn {
                items(playlists) { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Assuming we are adding the *current* song to this playlist
                                // But wait, this composable needs to know *which* song.
                                // The caller handles the action.
                                onAddToPlaylist(playlist, emptyList())
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Placeholder Icon
                         Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0xFF2C2C36), MaterialTheme.shapes.medium),
                             contentAlignment = Alignment.Center
                        ) {
                            Text(playlist.name.take(1).uppercase(), color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(playlist.name, color = Color.White)
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showCreateDialog) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Playlist") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onCreatePlaylist(newName)
                            showCreateDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
