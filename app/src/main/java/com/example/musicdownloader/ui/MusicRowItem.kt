package com.example.musicdownloader.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.musicdownloader.ui.ElectricPurple
import com.example.musicdownloader.utils.HapticUtils

@Composable
fun MusicRowItem(
    title: String,
    subtitle: String, // Changed from artist to subtitle to support "Artist • Album"
    thumbnailUrl: String,
    isLibrary: Boolean = true,
    isDownloaded: Boolean = false,
    downloadProgress: Float? = null,
    isWaiting: Boolean = false,
    isCached: Boolean = false,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit = {},
    onOptionClick: () -> Unit = {},
    showDownloadButton: Boolean = true
) {
    val context = LocalContext.current

    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClick()
            }
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Image(
                    painter = rememberAsyncImagePainter(thumbnailUrl),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                if (isCached && !isDownloaded && !isWaiting) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                            .size(16.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlashOn,
                            contentDescription = "Instant Play",
                            tint = PremiumGold,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                if (isWaiting) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = PremiumGold,
                            strokeWidth = 2.dp
                        )
                    }
                } else if (downloadProgress != null && downloadProgress > 0f && downloadProgress < 100f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier.size(24.dp),
                            color = PremiumGold,
                            trackColor = Color.White.copy(alpha = 0.3f),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold, // Bold as requested
                    maxLines = 1, // Max 1 line as requested
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (isWaiting) {
                    Text(
                        text = "Preparing...",
                        style = MaterialTheme.typography.labelSmall,
                        color = PremiumGold
                    )
                } else if (downloadProgress != null && downloadProgress > 0f && downloadProgress < 100f) {
                     Text(
                        text = "${downloadProgress.toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = PremiumGold
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isLibrary) {
                // Show download button for stream songs in library
                if (!isDownloaded && !isWaiting && (downloadProgress == null || downloadProgress == 0f)) {
                    IconButton(onClick = {
                        HapticUtils.performHapticFeedback(context)
                        onDownloadClick()
                    }) {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Download",
                            tint = ElectricPurple
                        )
                    }
                }

                IconButton(onClick = onOptionClick) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = Color.Gray
                    )
                }
            } else if (showDownloadButton) {
                if (isDownloaded) {
                     Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                } else if (!isWaiting && (downloadProgress == null || downloadProgress == 0f)) {
                    IconButton(onClick = {
                        HapticUtils.performHapticFeedback(context)
                        onDownloadClick()
                    }) {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Download",
                            tint = ElectricPurple
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MusicCard(
    title: String,
    subtitle: String, // Changed from artist to subtitle
    thumbnailUrl: String,
    isDownloaded: Boolean,
    downloadProgress: Float?,
    isWaiting: Boolean = false,
    isCached: Boolean = false,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
        .width(160.dp)
        .height(220.dp)
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        onClick = onClick
    ) {
        Column {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Image(
                    painter = rememberAsyncImagePainter(thumbnailUrl),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                if (isCached && !isDownloaded && !isWaiting) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(24.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlashOn,
                            contentDescription = "Instant Play",
                            tint = PremiumGold,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (isWaiting) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = PremiumGold,
                            strokeWidth = 3.dp
                        )
                    }
                } else if (downloadProgress != null && downloadProgress > 0f && downloadProgress < 100f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                         Column(horizontalAlignment = Alignment.CenterHorizontally) {
                             CircularProgressIndicator(
                                 progress = { downloadProgress / 100f },
                                 modifier = Modifier.size(32.dp),
                                 color = PremiumGold,
                                 trackColor = Color.White.copy(alpha = 0.3f),
                             )
                             Spacer(modifier = Modifier.height(4.dp))
                             Text("${downloadProgress.toInt()}%", color = Color.White, fontSize = 12.sp)
                         }
                    }
                } else if (isDownloaded) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                } else {
                     Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Download",
                                tint = ElectricPurple,
                                modifier = Modifier.size(24.dp).align(Alignment.Center)
                            )
                        }
                    }
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, fontSize = 14.sp)
                Text(subtitle, color = Color.Gray, maxLines = 1, fontSize = 12.sp)
            }
        }
    }
}
