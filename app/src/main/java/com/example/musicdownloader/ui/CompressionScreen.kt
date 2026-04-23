package com.example.musicdownloader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicdownloader.MusicViewModel
import com.example.musicdownloader.data.CompressionManager
import com.example.musicdownloader.data.CompressionQuality
import com.example.musicdownloader.data.Song
import com.example.musicdownloader.ui.ElectricPurple
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressionScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit
) {
    val songs by viewModel.librarySongs.collectAsStateWithLifecycle()
    val isCompressing by viewModel.isCompressing.collectAsStateWithLifecycle()
    val progressMessage by viewModel.compressionProgress.collectAsStateWithLifecycle()

    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedQuality by remember { mutableStateOf(CompressionQuality.MAX_COMPRESSION) }
    var showQualityDropdown by remember { mutableStateOf(false) }

    // Load file sizes asynchronously
    val fileSizes = produceState<Map<String, Long>>(initialValue = emptyMap(), key1 = songs) {
        value = withContext(Dispatchers.IO) {
            songs.associate { it.id to File(it.filePath).length() }
        }
    }

    val selectedSongs = remember(selectedIds, songs) {
        songs.filter { it.id in selectedIds }
    }

    // Stats
    val currentTotalSize = remember(selectedSongs, fileSizes.value) {
        selectedSongs.sumOf { fileSizes.value[it.id] ?: 0L }
    }

    val estimatedNewSize = remember(selectedSongs, selectedQuality) {
        selectedSongs.sumOf { CompressionManager.estimateSize(it, selectedQuality) }
    }

    val savedSize = currentTotalSize - estimatedNewSize
    val savedSizeStr = formatFileSize(savedSize.coerceAtLeast(0))

    BackHandler(enabled = !isCompressing) {
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Audio Compression", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isCompressing) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F0F13))
            )
        },
        bottomBar = {
            if (selectedIds.isNotEmpty() && !isCompressing) {
                Button(
                    onClick = {
                        viewModel.compressSongs(selectedSongs, selectedQuality)
                        selectedIds = emptySet() // Clear selection
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Compress ${selectedIds.size} Songs", fontWeight = FontWeight.Bold)
                        Text(
                            "Save approx. $savedSizeStr",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        },
        containerColor = Color(0xFF0F0F13)
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            Column {
                // Controls
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C26)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Compression Quality", color = Color.Gray, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))

                        Box {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showQualityDropdown = true }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(selectedQuality.displayName, color = Color.White, fontWeight = FontWeight.Bold)
                                    Text("Target Bitrate: ${selectedQuality.bitrateVal}", color = Color.Gray, fontSize = 12.sp)
                                }
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
                            }

                            DropdownMenu(
                                expanded = showQualityDropdown,
                                onDismissRequest = { showQualityDropdown = false },
                                modifier = Modifier.background(Color(0xFF2C2C36))
                            ) {
                                CompressionQuality.values().forEach { quality ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(quality.displayName, color = Color.White)
                                                Text("~${quality.approxBitrateKbps} kbps", color = Color.Gray, fontSize = 12.sp)
                                            }
                                        },
                                        onClick = {
                                            selectedQuality = quality
                                            showQualityDropdown = false
                                        }
                                    )
                                }
                            }
                        }

                        Divider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 8.dp))

                        // Select All
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                selectedIds = if (selectedIds.size == songs.size) emptySet() else songs.map { it.id }.toSet()
                            }
                        ) {
                            Checkbox(
                                checked = selectedIds.size == songs.size && songs.isNotEmpty(),
                                onCheckedChange = {
                                    selectedIds = if (it) songs.map { s -> s.id }.toSet() else emptySet()
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = ElectricPurple,
                                    uncheckedColor = Color.Gray
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (selectedIds.size == songs.size) "Deselect All" else "Select All (${songs.size} songs)",
                                color = Color.White
                            )
                        }
                    }
                }

                // List
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(songs, key = { it.id }) { song ->
                        val isSelected = selectedIds.contains(song.id)
                        val size = fileSizes.value[song.id] ?: 0L

                        CompressionRowItem(
                            song = song,
                            fileSize = size,
                            isSelected = isSelected,
                            onToggle = {
                                selectedIds = if (isSelected) {
                                    selectedIds - song.id
                                } else {
                                    selectedIds + song.id
                                }
                            }
                        )
                        Divider(color = Color.White.copy(alpha = 0.05f))
                    }
                }
            }

            // Loading Overlay
            if (isCompressing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C26)),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = ElectricPurple)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Optimizing Library...", color = Color.White, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(progressMessage, color = Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Please do not close the app.", color = Color.Red, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CompressionRowItem(
    song: Song,
    fileSize: Long,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = ElectricPurple,
                uncheckedColor = Color.Gray
            )
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(song.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${song.artist} • ${formatFileSize(fileSize)}", color = Color.Gray, fontSize = 12.sp, maxLines = 1)
        }
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}
