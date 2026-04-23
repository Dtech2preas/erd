package com.example.musicdownloader.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.example.musicdownloader.MusicViewModel
import com.example.musicdownloader.VideoItem
import com.example.musicdownloader.ui.ElectricPurple
import com.example.musicdownloader.ui.DTechBlue
import com.example.musicdownloader.ui.PremiumGold
import java.util.Calendar
import com.example.musicdownloader.R

@Composable
fun HomeScreen(viewModel: MusicViewModel, onSongClick: (String) -> Unit) {
    val homeFeedState by viewModel.uiState.collectAsStateWithLifecycle()
    val librarySongs by viewModel.librarySongs.collectAsStateWithLifecycle()
    val recommendedSongs by viewModel.recommendedSongs.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val initializingDownloads by viewModel.initializingDownloads.collectAsStateWithLifecycle()
    val playHistory by viewModel.playHistory.collectAsStateWithLifecycle()
    val cachedStreamIds by viewModel.cachedStreamIds.collectAsStateWithLifecycle()

    // Map of downloaded song IDs for quick lookup
    val downloadedIds = remember(librarySongs) {
        librarySongs.filter { !it.filePath.startsWith("stream://") }.map { it.id }.toSet()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Dynamic Header
        GreetingHeader()

        Spacer(modifier = Modifier.height(16.dp))

        // Randomize the "Made for You" list only when feeds actually change,
        // to prevent the UI from reshuffling when a user clicks play/download.
        val displayMadeForYou = remember(homeFeedState.madeForYou) {
            homeFeedState.madeForYou.shuffled()
        }

        // Compute deduplicated lists sequentially to prevent UI state loop
        val deduplicatedLists = remember(recommendedSongs, playHistory, homeFeedState.genreFeeds, downloadedIds, displayMadeForYou) {
            val seenIds = mutableSetOf<String>()

            val recommended = recommendedSongs.filter { !downloadedIds.contains(it.id) && seenIds.add(it.id) }
            val history = playHistory.filter { seenIds.add(it.songId) }

            // "Made for You" is pre-deduplicated in Repository to ensure count,
            // but we add them to seenIds so they aren't stolen by trending feeds
            displayMadeForYou.forEach { seenIds.add(it.id) }

            val trendingFeeds = homeFeedState.genreFeeds.map { feed ->
                feed.copy(songs = feed.songs.filter { seenIds.add(it.id) })
            }

            Triple(recommended, history, trendingFeeds)
        }

        val displayRecommended = deduplicatedLists.first
        val displayHistory = deduplicatedLists.second
        val deduplicatedTrendingFeeds = deduplicatedLists.third

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.weight(1f)
        ) {
            // 0. Recommended For You (Cached/Auto)
            if (displayRecommended.isNotEmpty()) {
                item {
                    Text(
                        text = "Recommended For You",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        items(displayRecommended, key = { it.id }) { song ->
                            val videoItem = VideoItem(
                                id = song.id,
                                title = song.title,
                                uploader = song.artist,
                                duration = song.duration,
                                thumbnailUrl = song.thumbnailUrl,
                                webUrl = "https://youtube.com/watch?v=${song.id}"
                            )

                            MusicCard(
                                title = song.title,
                                subtitle = song.artist,
                                thumbnailUrl = song.thumbnailUrl,
                                isDownloaded = false,
                                downloadProgress = null,
                                isWaiting = false,
                                isCached = cachedStreamIds.contains(song.id),
                                onClick = { viewModel.playStream(videoItem) },
                                onDownload = { viewModel.downloadSong(videoItem) },
                                modifier = Modifier.width(126.dp).height(180.dp)
                            )
                        }
                    }
                }
            }

            // 1. Recently Played Section
            if (displayHistory.isNotEmpty()) {
                item {
                    Text(
                        text = "Recently Played",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(displayHistory, key = { "history_${it.songId}" }) { historyItem ->
                            // Convert History to VideoItem for Card
                            // History item doesn't have album info usually, so we just use artist.
                            // Unless we fetch it or store it. PlayHistory struct: songId, title, artist, thumbnailUrl.
                            val song = VideoItem(
                                id = historyItem.songId,
                                title = historyItem.title,
                                duration = "",
                                uploader = historyItem.artist,
                                thumbnailUrl = historyItem.thumbnailUrl,
                                webUrl = "https://www.youtube.com/watch?v=${historyItem.songId}"
                            )
                            MusicCard(
                                title = song.title,
                                subtitle = song.uploader,
                                thumbnailUrl = song.thumbnailUrl,
                                isDownloaded = downloadedIds.contains(song.id),
                                    downloadProgress = downloadProgress[song.id]?.progress,
                                isWaiting = initializingDownloads.contains(song.id),
                                isCached = cachedStreamIds.contains(song.id),
                                onClick = {
                                    if (downloadedIds.contains(song.id)) {
                                        viewModel.playSong(song.id, song.title, song.uploader, song.thumbnailUrl)
                                    } else {
                                        viewModel.playStream(song)
                                    }
                                },
                                onDownload = { viewModel.downloadSong(song) },
                                modifier = Modifier.width(102.dp).height(136.dp) // Compact size
                            )
                        }
                    }
                }
            }

            if (homeFeedState.isLoading) {
                // Skeleton Loading
                item {
                     Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SkeletonLoader(modifier = Modifier.fillMaxWidth().height(200.dp))
                        SkeletonLoader(modifier = Modifier.fillMaxWidth().height(100.dp))
                    }
                }
            } else if (homeFeedState.errorMessage != null) {
                item {
                    Text(text = "Error: ${homeFeedState.errorMessage}", color = Color.Red)
                }
            } else {
                 // Combine all genres to create a randomized "Made for You" list
                 if (displayMadeForYou.isNotEmpty()) {
                     item {
                         Column {
                             Text(
                                 text = "Made for You",
                                 fontSize = 20.sp,
                                 fontWeight = FontWeight.Bold,
                                 color = Color.White,
                                 modifier = Modifier.padding(bottom = 12.dp)
                             )

                             // Horizontal Grid (simulated with Column of 10 items per chunk)
                             // So it scrolls horizontally, but has 10 items stacked vertically
                             val chunks = displayMadeForYou.chunked(10)
                             LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                 items(chunks) { chunk ->
                                     Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                         chunk.forEach { song ->
                                             val subtitle = if (song.album != null && song.album != "Unknown Album") "${song.uploader} • ${song.album}" else song.uploader
                                             MusicCard(
                                                 title = song.title,
                                                 subtitle = subtitle,
                                                 thumbnailUrl = song.thumbnailUrl,
                                                 isDownloaded = downloadedIds.contains(song.id),
                                                 downloadProgress = downloadProgress[song.id]?.progress,
                                                 isWaiting = initializingDownloads.contains(song.id),
                                                 isCached = cachedStreamIds.contains(song.id),
                                                 onClick = {
                                                     if (downloadedIds.contains(song.id)) {
                                                         viewModel.playSong(song.id, song.title, song.uploader, song.thumbnailUrl)
                                                     } else {
                                                         viewModel.playStream(song)
                                                     }
                                                 },
                                                 onDownload = { viewModel.downloadSong(song) },
                                                 modifier = Modifier.width(160.dp).height(220.dp)
                                             )
                                         }
                                     }
                                 }
                             }
                         }
                     }
                 }

                 // Display each individual genre as a single horizontal row
                 deduplicatedTrendingFeeds.forEach { feed ->
                     val displayTrending = feed.songs

                     if (displayTrending.isNotEmpty()) {
                         item {
                             Column {
                                 Row(
                                     modifier = Modifier.padding(bottom = 8.dp),
                                     verticalAlignment = Alignment.CenterVertically
                                 ) {
                                     Text(
                                         text = "Trending in ",
                                         fontSize = 20.sp,
                                         fontWeight = FontWeight.Bold,
                                         color = Color.White
                                     )
                                     Text(
                                         text = feed.genreName,
                                         fontSize = 20.sp,
                                         fontWeight = FontWeight.Bold,
                                         color = PremiumGold
                                     )
                                 }

                                 LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                     items(displayTrending, key = { "trending_${it.id}" }) { song ->
                                         val subtitle = if (song.album != null && song.album != "Unknown Album") "${song.uploader} • ${song.album}" else song.uploader
                                         MusicCard(
                                             title = song.title,
                                             subtitle = subtitle,
                                             thumbnailUrl = song.thumbnailUrl,
                                             isDownloaded = downloadedIds.contains(song.id),
                                             downloadProgress = downloadProgress[song.id]?.progress,
                                             isWaiting = initializingDownloads.contains(song.id),
                                             isCached = cachedStreamIds.contains(song.id),
                                             onClick = {
                                                 if (downloadedIds.contains(song.id)) {
                                                     viewModel.playSong(song.id, song.title, song.uploader, song.thumbnailUrl)
                                                 } else {
                                                     viewModel.playStream(song)
                                                 }
                                             },
                                             onDownload = { viewModel.downloadSong(song) },
                                             modifier = Modifier.width(160.dp).height(220.dp)
                                         )
                                     }
                                 }
                             }
                         }
                     }
                 }
            }
        }
    }
}

@Composable
fun GreetingHeader() {
    val calendar = Calendar.getInstance()
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 0..11 -> "Morning"
        in 12..17 -> "Afternoon"
        else -> "Evening"
    }

    // Animated Gradient
    val infiniteTransition = rememberInfiniteTransition(label = "header_gradient")
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset"
    )

    // Blue and Black Gradient as requested
    val brush = Brush.linearGradient(
        colors = listOf(DTechBlue, Color.Black, DTechBlue),
        start = Offset(offset, 0f),
        end = Offset(offset + 500f, 100f),
        tileMode = TileMode.Mirror
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        // D-Tech Logo
        Image(
            painter = painterResource(id = R.drawable.dtech_logo),
            contentDescription = "DTECH Logo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = "$greeting from DTECH",
            style = TextStyle(
                brush = brush,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}
