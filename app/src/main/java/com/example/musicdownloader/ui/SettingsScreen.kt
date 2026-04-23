package com.example.musicdownloader.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.musicdownloader.CookieManager
import com.example.musicdownloader.MusicViewModel
import com.example.musicdownloader.UserPreferences
import com.example.musicdownloader.workers.StreamRefresherWorker

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onShowLogs: () -> Unit,
    onNavigateToInfo: () -> Unit,
    onNavigateToCompression: () -> Unit,
    contentPadding: PaddingValues
) {
    val viewModel: MusicViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val context = LocalContext.current
    val dnaStats by viewModel.dnaStats.collectAsState()

    var showCookieDialog by remember { mutableStateOf(false) }

    // Manage Genres & Artists State
    var savedGenres by remember { mutableStateOf(UserPreferences.getGenres(context)) }
    var newGenreText by remember { mutableStateOf("") }
    var savedArtists by remember { mutableStateOf(UserPreferences.getArtists(context)) }
    var newArtistText by remember { mutableStateOf("") }

    // Toggle for DNA Dashboard
    var isDnaVisible by remember { mutableStateOf(false) }

    // High End Mode State
    var isHighEndMode by remember { mutableStateOf(UserPreferences.isHighEndModeEnabled(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // --- 1. PROFILE HEADER ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar Placeholder
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Music Lover", // Placeholder name
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = dnaStats.personalityType, // Dynamic Personality
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // DNA Toggle Button
            Button(
                onClick = { isDnaVisible = !isDnaVisible },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDnaVisible) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (isDnaVisible) "Hide DNA" else "View DNA")
            }
        }

        // --- 2. DNA DASHBOARD (Collapsible) ---
        AnimatedVisibility(
            visible = isDnaVisible,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                DnaDashboard(stats = dnaStats)
                Spacer(modifier = Modifier.height(32.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // --- 3. APPEARANCE ---
        SettingsSectionTitle(title = "Appearance", icon = Icons.Default.Face)
        SettingsCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Accent Color", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val colors = listOf(
                        0xFF7D5FFF, 0xFFE91E63, 0xFF00E5FF, 0xFF4CAF50, 0xFFFFEB3B, 0xFFFF5722
                    )
                    colors.forEach { colorLong ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(colorLong))
                                .clickable {
                                    UserPreferences.setThemeColor(context, colorLong)
                                    ThemeManager.updateTheme(colorLong)
                                }
                        )
                    }
                }
            }
        }

        // --- 4. LIBRARY & AUDIO ---
        SettingsSectionTitle(title = "Library & Audio", icon = Icons.Default.Settings)
        SettingsCard {
            Column(modifier = Modifier.padding(16.dp)) {
                // High End Mode Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "High-End Mode",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Auto-fetch URLs for all visible songs. Uses more data.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isHighEndMode,
                        onCheckedChange = {
                            isHighEndMode = it
                            UserPreferences.setHighEndModeEnabled(context, it)
                            if (it) {
                                // Trigger immediate refresh if enabled
                                Toast.makeText(context, "High-End Mode Enabled", Toast.LENGTH_SHORT).show()
                                val request = OneTimeWorkRequestBuilder<StreamRefresherWorker>()
                                    .addTag("refresh_streams_manual")
                                    .build()
                                WorkManager.getInstance(context).enqueue(request)
                            }
                        }
                    )
                }
                Divider(color = MaterialTheme.colorScheme.background)

                // Smart Shuffle Buffer
                var bufferSize by remember { mutableIntStateOf(UserPreferences.getSmartShuffleBuffer(context)) }
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text = "Smart Shuffle Pre-load: $bufferSize songs",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = bufferSize.toFloat(),
                        onValueChange = { bufferSize = it.toInt() },
                        onValueChangeFinished = { UserPreferences.setSmartShuffleBuffer(context, bufferSize) },
                        valueRange = 1f..10f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Text(
                        text = "Higher value = smoother playback but more data/storage usage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
                Divider(color = MaterialTheme.colorScheme.background)


                // Import Local
                val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
                     androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                 ) { isGranted ->
                     if (isGranted) viewModel.importLocalSongs()
                     else Toast.makeText(context, "Permission Denied", Toast.LENGTH_SHORT).show()
                 }

                SettingsActionRow(
                    label = "Import Local Songs",
                    icon = Icons.Default.Add,
                    onClick = {
                         val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                             android.Manifest.permission.READ_MEDIA_AUDIO
                         } else {
                             android.Manifest.permission.READ_EXTERNAL_STORAGE
                         }
                         if (androidx.core.content.ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                             viewModel.importLocalSongs()
                         } else {
                             launcher.launch(permission)
                         }
                    }
                )
                Divider(color = MaterialTheme.colorScheme.background)

                // Rescan
                SettingsActionRow(
                    label = "Scan / Fix Metadata",
                    icon = Icons.Default.Refresh,
                    onClick = { viewModel.rescanLibrary() }
                )
                Divider(color = MaterialTheme.colorScheme.background)

                // Compression
                SettingsActionRow(
                    label = "Audio Compression",
                    icon = Icons.Default.Info, // Generic info icon
                    onClick = onNavigateToCompression
                )
            }
        }

        // --- 5. PREFERENCES (Genres & Artists) ---
        SettingsSectionTitle(title = "Your Vibe", icon = Icons.Default.Favorite)
        SettingsCard {
            Column(modifier = Modifier.padding(16.dp)) {
                // Genres section
                Text("Your Genres:", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    savedGenres.sorted().forEach { genre ->
                        InputChip(
                            selected = true,
                            onClick = {
                                UserPreferences.removeGenre(context, genre)
                                savedGenres = UserPreferences.getGenres(context)
                            },
                            label = { Text(genre) },
                            trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove") }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newGenreText,
                        onValueChange = { newGenreText = it },
                        label = { Text("Add Genre") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = {
                        if (newGenreText.isNotBlank()) {
                            UserPreferences.addGenre(context, newGenreText.trim())
                            savedGenres = UserPreferences.getGenres(context)
                            newGenreText = ""
                        }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Divider(color = MaterialTheme.colorScheme.background)
                Spacer(modifier = Modifier.height(24.dp))

                // Artists section
                Text("Your Artists:", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    savedArtists.sorted().forEach { artist ->
                        InputChip(
                            selected = true,
                            onClick = {
                                UserPreferences.removeArtist(context, artist)
                                savedArtists = UserPreferences.getArtists(context)
                            },
                            label = { Text(artist) },
                            trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove") }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newArtistText,
                        onValueChange = { newArtistText = it },
                        label = { Text("Add Artist") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = {
                        if (newArtistText.isNotBlank()) {
                            UserPreferences.addArtist(context, newArtistText.trim())
                            savedArtists = UserPreferences.getArtists(context)
                            newArtistText = ""
                        }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                    }
                }
            }
        }

        // --- 6. SUPPORT ---
        SettingsSectionTitle(title = "Support", icon = Icons.Default.Favorite)
        SettingsCard {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingsActionRow(
                    label = "App Info & Guide",
                    icon = Icons.Default.Info,
                    onClick = onNavigateToInfo
                )
                Divider(color = MaterialTheme.colorScheme.background)
                SettingsActionRow(
                    label = "Join Telegram Channel",
                    icon = Icons.Default.Send,
                    onClick = {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/DTECHX24"))
                        context.startActivity(intent)
                    }
                )
            }
        }

        // --- 7. DEVELOPER ---
        SettingsSectionTitle(title = "Advanced", icon = Icons.Default.Build)
        SettingsCard {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingsActionRow(
                    label = "Show Debug Logs",
                    icon = Icons.Default.Info,
                    onClick = onShowLogs
                )
                Divider(color = MaterialTheme.colorScheme.background)
                SettingsActionRow(
                    label = "Set YouTube Cookies",
                    icon = Icons.Default.Lock,
                    onClick = { showCookieDialog = true }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        CenterText(text = "App Version: 1.2 (DTECH DNA UPDATE)")
        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showCookieDialog) {
        CookieDialog(
            onDismiss = { showCookieDialog = false },
            onSave = { cookie ->
                CookieManager.saveCookie(context, cookie)
                showCookieDialog = false
                Toast.makeText(context, "Cookie Saved", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

// --- HELPER COMPONENTS ---

@Composable
fun SettingsSectionTitle(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        content = { content() }
    )
}

@Composable
fun SettingsActionRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    iconTint: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun CenterText(text: String) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
