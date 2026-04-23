package com.example.musicdownloader.ui

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.musicdownloader.InstanceRegistry
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.musicdownloader.MusicViewModel
import com.example.musicdownloader.VideoItem
import com.example.musicdownloader.data.Song
import com.example.musicdownloader.ui.AddToPlaylistSheet
import com.example.musicdownloader.utils.HapticUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// --- Premium Theme Constants ---
private val DeepBlack = Color(0xFF050510)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFB0B0B0)
private val AccentBlue = Color(0xFF2962FF) // D-Tech Blue
private val GlassWhite = Color(0x1AFFFFFF) // 10% White for glass effect

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FullScreenPlayer(
    viewModel: MusicViewModel,
    onCollapse: () -> Unit
) {
    val currentSongStatus by viewModel.currentSongStatus.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val shuffleModeEnabled by viewModel.shuffleModeEnabled.collectAsState()
    val smartShuffleEnabled by viewModel.isSmartShuffleEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val audioSessionId by viewModel.audioSessionId.collectAsState()

    val context = LocalContext.current

    val currentMediaItem = currentSongStatus.mediaItem
    val currentSongId = currentMediaItem?.mediaId
    val isLiked = currentSongStatus.isLiked
    val isSavedToLibrary = currentSongStatus.isInLibrary

    // Dynamic Color Extraction
    var dominantColor by remember { mutableStateOf(AccentBlue) }
    val animatedColor by animateColorAsState(targetValue = dominantColor, animationSpec = tween(1000), label = "color")

    // Add to Playlist Sheet State
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showMoreOptions by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showPlaybackSpeedDialog by remember { mutableStateOf(false) }

    var showLyrics by remember { mutableStateOf(false) }
    var currentLyrics by remember { mutableStateOf<String?>(null) }
    val lyricsHelper = InstanceRegistry.lyricsHelper

    if (currentMediaItem == null) return

    val artworkUri = currentMediaItem?.mediaMetadata?.artworkUri
    val title = currentMediaItem?.mediaMetadata?.title?.toString() ?: "Unknown Title"
    val artist = currentMediaItem?.mediaMetadata?.artist?.toString() ?: "Unknown Artist"

    LaunchedEffect(currentSongId, showLyrics) {
        if (currentSongId != null && showLyrics) {
            currentLyrics = null // Reset before fetching
            currentLyrics = lyricsHelper?.getLyrics(currentSongId, title, artist, duration)
        }
    }

    // Extract Palette
    LaunchedEffect(artworkUri) {
        if (artworkUri != null) {
            withContext(Dispatchers.IO) {
                val loader = ImageLoader(context)
                val req = ImageRequest.Builder(context)
                    .data(artworkUri)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(req)
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    val p = Palette.from(bitmap).generate()
                    val colorInt = p.getVibrantColor(
                        p.getDarkVibrantColor(AccentBlue.toArgb())
                    )
                    dominantColor = Color(colorInt)
                }
            }
        }
    }

    // Determine Status Text
    val uri = currentMediaItem?.localConfiguration?.uri
    val statusText = remember(uri) {
        when {
            uri?.scheme == "file" -> "Offline Playback"
            uri?.scheme == "dtech" -> "Hi-Res Audio"
            else -> "Streaming"
        }
    }

    // Infinite Animation for "Breathing" Effect
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val breatheAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(modifier = Modifier.fillMaxSize().background(DeepBlack)) {
        // 1. Background Layers
        // Layer A: Dimmed, blurred artwork filling the screen
        Image(
            painter = rememberAsyncImagePainter(artworkUri),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .blur(80.dp) // Heavy blur for abstract background
                .alpha(0.3f),
            contentScale = ContentScale.Crop
        )

        // Layer B: Dynamic Gradient Mesh
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            animatedColor.copy(alpha = 0.15f), // Slight tint at top
                            DeepBlack.copy(alpha = 0.6f),      // Darker middle
                            DeepBlack.copy(alpha = 0.95f)      // Almost black bottom
                        )
                    )
                )
        )

        // 2. Main Content Container
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- Header ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = TextPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "NOW PLAYING",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = animatedColor, // Dynamic accent color
                        fontSize = 10.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showLyrics = !showLyrics }) {
                        Icon(
                            imageVector = if (showLyrics) Icons.Rounded.Lyrics else Icons.Outlined.Lyrics,
                            contentDescription = "Toggle Lyrics",
                            tint = if (showLyrics) AccentBlue else TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Box {
                        IconButton(onClick = { showMoreOptions = true }) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "More",
                                tint = TextSecondary
                            )
                        }
                        DropdownMenu(
                        expanded = showMoreOptions,
                        onDismissRequest = { showMoreOptions = false },
                        modifier = Modifier.background(DeepBlack.copy(alpha = 0.95f))
                    ) {
                        // Sleep Timer
                        DropdownMenuItem(
                            text = { Text("Sleep Timer", color = TextPrimary) },
                            onClick = {
                                showMoreOptions = false
                                showSleepTimerDialog = true
                            },
                            leadingIcon = { Icon(Icons.Rounded.AccessTime, contentDescription = null, tint = TextSecondary) }
                        )
                        // Playback Speed
                        DropdownMenuItem(
                            text = { Text("Playback Speed", color = TextPrimary) },
                            onClick = {
                                showMoreOptions = false
                                showPlaybackSpeedDialog = true
                            },
                            leadingIcon = { Icon(Icons.Rounded.SlowMotionVideo, contentDescription = null, tint = TextSecondary) }
                        )
                    }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- Album Art or Lyrics ---
            Box(
                modifier = Modifier
                    .weight(1f) // Take up available space but leave room
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (showLyrics) {
                    Lyrics(
                        lyrics = currentLyrics,
                        currentPositionProvider = { currentPosition },
                        onSeek = { viewModel.seekTo(it) },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Glow Effect behind Art
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .aspectRatio(1f)
                            .offset(y = 20.dp)
                            .blur(40.dp)
                            .alpha(breatheAlpha)
                            .background(animatedColor.copy(alpha = 0.4f), CircleShape)
                    )

                    // Actual Art Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .aspectRatio(1f)
                            .shadow(
                                elevation = 20.dp,
                                spotColor = animatedColor,
                                ambientColor = DeepBlack,
                                shape = RoundedCornerShape(24.dp)
                            ),
                        shape = RoundedCornerShape(24.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(artworkUri),
                            contentDescription = "Album Art",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- Track Info ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                }

                // Add/Remove Library Toggle
                ScaleIconButton(
                    onClick = {
                        if (currentSongId != null) {
                            val videoItem = VideoItem(
                                id = currentSongId,
                                title = title,
                                uploader = artist,
                                duration = formatTime(duration),
                                thumbnailUrl = artworkUri?.toString() ?: "",
                                webUrl = "https://youtube.com/watch?v=$currentSongId"
                            )
                            viewModel.toggleLibraryStatus(videoItem, isSavedToLibrary)
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (isSavedToLibrary) Icons.Rounded.CheckCircle else Icons.Rounded.AddCircleOutline,
                        contentDescription = "Library",
                        tint = if (isSavedToLibrary) AccentBlue else TextSecondary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- Controls Area ---

            // 1. Progress Bar
            Column(modifier = Modifier.fillMaxWidth()) {
                var sliderPosition by remember { mutableFloatStateOf(0f) }
                var isDragging by remember { mutableStateOf(false) }

                LaunchedEffect(currentPosition, duration) {
                    if (!isDragging && duration > 0) {
                        sliderPosition = currentPosition.toFloat() / duration.toFloat()
                    }
                }

                // Custom Slider
                Slider(
                    value = sliderPosition,
                    onValueChange = {
                        isDragging = true
                        sliderPosition = it
                    },
                    onValueChangeFinished = {
                        viewModel.seekTo((sliderPosition * duration).toLong())
                        isDragging = false
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = TextPrimary,
                        activeTrackColor = TextPrimary,
                        inactiveTrackColor = TextSecondary.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                )

                // Timers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(currentPosition),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = formatTime(duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Playback Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle
                IconButton(onClick = { viewModel.toggleShuffle() }) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (shuffleModeEnabled) AccentBlue else TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Previous
                IconButton(
                    onClick = { viewModel.skipToPrevious() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous",
                        tint = TextPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Play/Pause (Hero Button)
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .shadow(16.dp, CircleShape, spotColor = AccentBlue)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(TextPrimary, Color(0xFFE0E0E0))
                            )
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = androidx.compose.material.ripple.rememberRipple(bounded = true, color = DeepBlack)
                        ) { viewModel.togglePlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.isLoadingPlayer) {
                        CircularProgressIndicator(
                            color = DeepBlack,
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = DeepBlack,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                }

                // Next
                IconButton(
                    onClick = { viewModel.skipToNext() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        tint = TextPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Repeat
                IconButton(onClick = { viewModel.toggleRepeatMode() }) {
                    Icon(
                        imageVector = if (repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        contentDescription = "Repeat",
                        tint = if (repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) AccentBlue else TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 3. Secondary Actions Row (Smart Shuffle, Playlist, Like)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Info / Smart Shuffle
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { viewModel.toggleSmartShuffle() }) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "Smart Shuffle",
                            tint = if (smartShuffleEnabled) AccentBlue else TextSecondary
                        )
                    }
                    Text(
                        text = if (smartShuffleEnabled) "On" else "Smart",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (smartShuffleEnabled) AccentBlue else TextSecondary.copy(alpha = 0.5f),
                        fontSize = 10.sp
                    )
                }

                // Playlist
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { showAddToPlaylistDialog = true }) {
                        Icon(
                            imageVector = Icons.Rounded.PlaylistAdd,
                            contentDescription = "Playlist",
                            tint = TextSecondary
                        )
                    }
                    Text(
                        text = "Add to Playlist",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary.copy(alpha = 0.5f),
                        fontSize = 10.sp
                    )
                }

                // Like
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = {
                        if (currentSongId != null) {
                            val videoItem = VideoItem(
                                id = currentSongId,
                                title = title,
                                uploader = artist,
                                duration = formatTime(duration),
                                thumbnailUrl = artworkUri?.toString() ?: "",
                                webUrl = "https://youtube.com/watch?v=$currentSongId"
                            )
                            viewModel.toggleLike(videoItem)
                        }
                    }) {
                        Icon(
                            imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (isLiked) Color.Red else TextSecondary // Red for heart
                        )
                    }
                    Text(
                        text = if (isLiked) "Liked" else "Like",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isLiked) Color.Red else TextSecondary.copy(alpha = 0.5f),
                        fontSize = 10.sp
                    )
                }
            }
        }

        // --- Visualizer (Subtle Overlay at Bottom) ---
        // Placing it behind content would be better, but Composition order matters.
        // If we want it at the bottom, we can place it here with alignment.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(120.dp) // Height of visualizer area
                .alpha(0.3f) // Subtle
                .padding(bottom = 0.dp), // Align to very bottom
            contentAlignment = Alignment.BottomCenter
        ) {
            RealtimeVisualizer(
                audioSessionId = audioSessionId,
                isPlaying = isPlaying,
                modifier = Modifier.fillMaxSize(),
                barCount = 32,
                barColor = animatedColor
            )
        }
    }

    // --- Dialogs ---
    if (showAddToPlaylistDialog) {
        val currentSong = Song(
            id = currentSongId ?: "",
            title = title,
            artist = artist,
            thumbnailUrl = artworkUri?.toString() ?: "",
            filePath = "", // Not needed for playlist logic usually
            duration = ""
        )
        AddToPlaylistSheet(
            playlists = playlists,
            songs = listOf(currentSong),
            onDismiss = { showAddToPlaylistDialog = false },
            onCreatePlaylist = { name -> viewModel.createPlaylist(name) },
            onAddToPlaylist = { playlist, _ ->
                if (currentSongId != null) {
                    val videoItem = VideoItem(
                        id = currentSongId,
                        title = title,
                        uploader = artist,
                        duration = formatTime(duration),
                        thumbnailUrl = artworkUri?.toString() ?: "",
                        webUrl = "https://youtube.com/watch?v=$currentSongId"
                    )
                    viewModel.addSongToPlaylist(playlist.id.toInt(), videoItem)
                }
                showAddToPlaylistDialog = false
            }
        )
    }

    if (showSleepTimerDialog) {
        val options = listOf(5, 10, 15, 30, 45, 60, 120)
        AlertDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            title = { Text("Sleep Timer", color = TextPrimary) },
            text = {
                Column {
                    options.forEach { minutes ->
                        Text(
                            text = "$minutes minutes",
                            color = TextSecondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.startSleepTimer(minutes)
                                    showSleepTimerDialog = false
                                }
                                .padding(vertical = 12.dp)
                        )
                    }
                    Divider(color = TextSecondary.copy(alpha = 0.2f))
                    Text(
                        text = "Cancel Timer",
                        color = Color.Red,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.cancelSleepTimer()
                                showSleepTimerDialog = false
                            }
                            .padding(vertical = 12.dp)
                    )
                }
            },
            confirmButton = {},
            containerColor = DeepBlack,
            textContentColor = TextSecondary
        )
    }

    if (showPlaybackSpeedDialog) {
        val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        AlertDialog(
            onDismissRequest = { showPlaybackSpeedDialog = false },
            title = { Text("Playback Speed", color = TextPrimary) },
            text = {
                Column {
                    speeds.forEach { speed ->
                        Text(
                            text = "${speed}x" + if (speed == 1.0f) " (Normal)" else "",
                            color = TextSecondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setPlaybackSpeed(speed)
                                    showPlaybackSpeedDialog = false
                                }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            containerColor = DeepBlack,
            textContentColor = TextSecondary
        )
    }
}

// Helper: Scalable Icon Button wrapper
@Composable
fun ScaleIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.9f else 1f, label = "scale")

    Box(
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null, // Custom ripple handled by parent if needed, or none
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

private fun formatTime(millis: Long): String {
    if (millis < 0) return "00:00"
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
