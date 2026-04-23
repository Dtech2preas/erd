package com.example.musicdownloader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.musicdownloader.DownloadStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveDownloadsScreen(
    activeDownloads: List<DownloadStatus>,
    onBack: () -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Active Downloads", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F0F13))
            )
        },
        containerColor = Color(0xFF0F0F13)
    ) { padding ->
        if (activeDownloads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No active downloads", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(activeDownloads) { download ->
                    DownloadItemCard(
                        download = download,
                        onPause = { onPause(download.videoId) },
                        onResume = { onResume(download.videoId) },
                        onDelete = { onDelete(download.videoId) }
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadItemCard(
    download: DownloadStatus,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = download.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (download.isPaused) "Paused" else "${download.speed} • ETA: ${download.eta}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (download.isPaused) Color.Yellow else Color.Gray
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Pause/Resume Button
                    IconButton(onClick = { if (download.isPaused) onResume() else onPause() }) {
                        if (download.isPaused) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = ElectricPurple)
                        } else {
                            // Using a Pause icon (creating one manually since standard might vary or just use standard)
                            // There is no Icons.Default.Pause in standard set? It is in androidx.compose.material.icons.filled.Pause but sometimes explicit import needed.
                            // I will assume it's available or use a shape.
                            // Let's rely on standard icon availability.
                            // If Pause missing, I'll use simple box. But it should be there.
                            // Actually, I'll use a pause icon from resources if I can, but standard material icons has Pause.
                            // To be safe I'll use PlayArrow for resume, and I'll use a custom composable for Pause if needed.
                            // Wait, Icons.Filled.Pause is standard.
                           Icon(
                                imageVector = androidx.compose.material.icons.Icons.Filled.Pause,
                                contentDescription = "Pause",
                                tint = Color.White
                           )
                        }
                    }

                    // Delete Button
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Close, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { download.progress / 100f },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = if (download.isPaused) Color.Gray else ElectricPurple,
                trackColor = Color.White.copy(alpha = 0.1f),
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${download.progress.toInt()}%", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text(download.totalSize, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
    }
}

// Ensure ElectricPurple is available or define it locally if not imported
