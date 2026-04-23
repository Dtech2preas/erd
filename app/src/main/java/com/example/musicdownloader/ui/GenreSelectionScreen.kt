package com.example.musicdownloader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp


import android.content.Intent
import android.app.PendingIntent
import android.app.AlarmManager
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenreSelectionScreen(
    onDone: (Set<String>, Set<String>) -> Unit
) {
    var selectedGenres by remember { mutableStateOf(setOf<String>()) }
    var customGenreText by remember { mutableStateOf("") }

    var selectedArtists by remember { mutableStateOf(setOf<String>()) }
    var customArtistText by remember { mutableStateOf("") }

    var showRestartPopup by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val genrePresets = listOf("Amapiano", "Hip Hop", "Afro Soul", "Gospel", "R&B", "Deep House", "Pop", "Jazz")

    if (showRestartPopup) {
        AlertDialog(
            onDismissRequest = { /* Force them to use the button */ },
            title = {
                Text("App Reload Required", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Please close the app and open it again to get your customized playlists, or click the Reload App button below.", style = MaterialTheme.typography.bodyLarge)
                    Text("Tip: Visit Settings to toggle 'High-End Mode' for faster new song playback, and increase the 'Prefetch Limit for Smart Shuffle'.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Version " + "X.24" + " - Smart Shuffle Update", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDone(selectedGenres, selectedArtists)
                        // Trigger App Restart
                        val packageManager = context.packageManager
                        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
                        val componentName = intent?.component
                        val mainIntent = Intent.makeRestartActivityTask(componentName)
                        context.startActivity(mainIntent)
                        Runtime.getRuntime().exit(0)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Reload App")
                }
            },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }

    Scaffold(
        bottomBar = {
            Button(
                onClick = { showRestartPopup = true },

                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(56.dp),
                enabled = selectedGenres.isNotEmpty() || selectedArtists.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Start Your Journey", style = MaterialTheme.typography.titleMedium)
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            TechBackground()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(modifier = Modifier.height(48.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(1),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    item {
                        Text(
                            text = "Add your vibe",
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                    item {
                        Text(
                            text = "Select your favourite genres and artists to build your unique feed.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    item { Spacer(modifier = Modifier.height(32.dp)) }

                    // --- GENRES SECTION ---
                    item {
                        Text("Favorite Genres", style = MaterialTheme.typography.titleLarge)
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = customGenreText,
                                onValueChange = { customGenreText = it },
                                label = { Text("Add Genre") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    if (customGenreText.isNotBlank()) {
                                        selectedGenres = selectedGenres + customGenreText.trim()
                                        customGenreText = ""
                                    }
                                },
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add")
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                    item {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            genrePresets.forEach { genre ->
                                val isSelected = selectedGenres.contains(genre)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedGenres = if (isSelected) selectedGenres - genre else selectedGenres + genre
                                    },
                                    label = { Text(genre) },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, contentDescription = null) }
                                    } else null
                                )
                            }
                            selectedGenres.filter { !genrePresets.contains(it) }.forEach { genre ->
                                FilterChip(
                                    selected = true,
                                    onClick = { selectedGenres = selectedGenres - genre },
                                    label = { Text(genre) },
                                    leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) }
                                )
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(32.dp)) }

                    // --- ARTISTS SECTION ---
                    item {
                        Text("Favorite Artists", style = MaterialTheme.typography.titleLarge)
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = customArtistText,
                                onValueChange = { customArtistText = it },
                                label = { Text("Add Artist") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    if (customArtistText.isNotBlank()) {
                                        selectedArtists = selectedArtists + customArtistText.trim()
                                        customArtistText = ""
                                    }
                                },
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add")
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                    item {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            selectedArtists.forEach { artist ->
                                FilterChip(
                                    selected = true,
                                    onClick = { selectedArtists = selectedArtists - artist },
                                    label = { Text(artist) },
                                    leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) }
                                )
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(56.dp)) }
                }
            }
        }
    }
}

@Composable
fun TechBackground() {
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val primaryColor = Color(0xFF2962FF) // AccentBlue

        // Draw grid lines
        val lineCount = 10
        val stepX = width / lineCount
        val stepY = height / lineCount

        for (i in 0..lineCount) {
             drawLine(
                 color = primaryColor.copy(alpha = 0.05f),
                 start = Offset(i * stepX, 0f),
                 end = Offset(i * stepX, height),
                 strokeWidth = 1.dp.toPx()
             )
             drawLine(
                 color = primaryColor.copy(alpha = 0.05f),
                 start = Offset(0f, i * stepY),
                 end = Offset(width, i * stepY),
                 strokeWidth = 1.dp.toPx()
             )
        }

        // Draw glowing circles
        drawCircle(
             color = primaryColor.copy(alpha = 0.08f),
             radius = 150.dp.toPx(),
             center = Offset(width * 0.85f, height * 0.15f)
        )
         drawCircle(
             color = primaryColor.copy(alpha = 0.08f),
             radius = 120.dp.toPx(),
             center = Offset(width * 0.15f, height * 0.85f)
        )
    }
}
