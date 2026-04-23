package com.example.musicdownloader

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.musicdownloader.ui.ElectricPurple

@Composable
fun MiniPlayer(viewModel: MusicViewModel, onClick: () -> Unit) {
    val currentMediaItem by viewModel.currentMediaItem.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()

    if (currentMediaItem == null) return

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp) // Slightly taller
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surfaceContainerHighest, // Modern container color
        shadowElevation = 8.dp,
        tonalElevation = 8.dp
    ) {
        Box {
            Row(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Artwork with rounded corners
                Image(
                    painter = rememberAsyncImagePainter(currentMediaItem?.mediaMetadata?.artworkUri),
                    contentDescription = null,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.DarkGray),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Title & Artist
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = currentMediaItem?.mediaMetadata?.title?.toString() ?: "Unknown Title",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentMediaItem?.mediaMetadata?.artist?.toString() ?: "Unknown Artist",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Play/Pause Button
                IconButton(onClick = { viewModel.togglePlayPause() }) {
                    if (uiState.isLoadingPlayer) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = ElectricPurple
                        )
                    } else {
                        if (isPlaying) {
                            // Manual pause icon since Icons.Default.Pause isn't always auto-imported
                             Icon(
                                 painter = painterResource(android.R.drawable.ic_media_pause),
                                 contentDescription = "Pause",
                                 modifier = Modifier.size(32.dp),
                                 tint = ElectricPurple
                             )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                modifier = Modifier.size(32.dp),
                                tint = ElectricPurple
                            )
                        }
                    }
                }
            }

            // Progress Bar at Bottom
            if (duration > 0) {
                LinearProgressIndicator(
                    progress = { (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .align(Alignment.BottomCenter),
                    color = ElectricPurple,
                    trackColor = Color.Transparent
                )
            }
        }
    }
}
