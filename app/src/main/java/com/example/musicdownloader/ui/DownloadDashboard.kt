package com.example.musicdownloader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicdownloader.DownloadStatus

@Composable
fun DownloadDashboard(
    downloads: List<DownloadStatus>,
    onCancel: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (downloads.isEmpty()) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Downloads in Progress (${downloads.size})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            downloads.forEach { download ->
                DownloadItemRow(download = download, onCancel = onCancel)
                if (download != downloads.last()) {
                    Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                }
            }
        }
    }
}

@Composable
fun DownloadItemRow(
    download: DownloadStatus,
    onCancel: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Title & Speed
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Downloading ID: ${download.videoId}", // Ideally we'd map this to a Title if we had it.
                    // Since DownloadStatus only has videoId, we might want to pass a title map or just show ID/placeholder.
                    // Wait, MusicViewModel has 'initializingDownloads' which are just IDs.
                    // But usually the User searches, clicks download, and stays on screen.
                    // The 'VideoItem' title isn't stored in DownloadStatus currently.
                    // IMPROVEMENT: I should probably pass title to DownloadStatus or look it up.
                    // For now, I'll use "Song ${download.videoId.take(5)}..." or similar if title missing.
                    // Actually, the Worker gets the title. YoutubeClient doesn't know the title when parsing logs.
                    // I'll leave it as "Processing..." or ID for now, but I can improve this later.
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${download.speed} • ETA: ${download.eta}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            IconButton(onClick = { onCancel(download.videoId) }) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Progress Bar & Stats
        Column {
            LinearProgressIndicator(
                progress = { download.progress / 100f },
                modifier = Modifier.fillMaxWidth().height(6.dp).background(Color.Transparent, RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${download.progress.toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${download.totalSize}", // "10 MB"
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
