package com.example.musicdownloader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoScreen(
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Info & Guide") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- 1. ENGINEERING & TECH ---
            item {
                InfoSectionHeader("Engineering & Tech")
            }
            item {
                InfoCard(
                    title = "Premium Experience for Free",
                    description = "We engineered this app to deliver a premium music experience on par with top services like Spotify, but completely free. Our goal is to make high-quality music accessible to everyone."
                )
            }
            item {
                InfoCard(
                    title = "Speed Optimization & High-End Mode",
                    description = "We've vastly improved loading speeds. Standard streaming is fast, but if you enable 'High-End Mode' in Settings, the app works in the background to pre-fetch your library songs, offering near-instant, offline-like playback."
                )
            }
            item {
                InfoCard(
                    title = "How Searches Work",
                    description = "We utilize specialized high-speed internal APIs (InnerTube) to fetch search results instantly. This ensures you find your favorite songs without delay."
                )
            }
            item {
                InfoCard(
                    title = "Streaming & Downloads",
                    description = "We prioritize fast native APIs for instant streaming. For downloading offline copies, we utilize advanced audio extraction technology to retrieve high-quality audio streams directly from the source."
                )
            }

            // --- 2. HOW TO USE ---
            item {
                Spacer(modifier = Modifier.height(16.dp))
                InfoSectionHeader("How to Use")
            }
            item {
                InfoCard(
                    title = "Playing Music",
                    description = "Simply click on any song to start streaming. Most songs start playing within a few seconds. Background playback is fully supported, so you can lock your screen or use other apps while listening."
                )
            }
            item {
                InfoCard(
                    title = "Downloading",
                    description = "Tap the small download icon next to any song to add it to your library. For stream songs in your library, you can download them for offline playback."
                )
            }
            item {
                InfoCard(
                    title = "Full Screen Player",
                    description = "Tap the mini-player at the bottom of the screen to open the Full Screen Player. Here you can access advanced controls like Shuffle, Repeat One, Repeat All, and Add to Library (for streamed songs)."
                )
            }
            item {
                InfoCard(
                    title = "Smart Shuffle",
                    description = "We are proud to introduce SMART SHUFFLE. This feature analyzes your listening history, favorite artists, and genres to automatically find and play songs you'll love. It's like having a personal DJ that knows your vibe!"
                )
            }

            // --- 3. PAGE GUIDE ---
            item {
                Spacer(modifier = Modifier.height(16.dp))
                InfoSectionHeader("App Pages Guide")
            }
            item {
                InfoCard(
                    title = "Home",
                    description = "Your personal dashboard featuring Recommended Songs (based on your taste), Recently Played tracks, and your favorite Genres."
                )
            }
            item {
                InfoCard(
                    title = "Search",
                    description = "Find any song you can think of. You can play it immediately or download it to your library."
                )
            }
            item {
                InfoCard(
                    title = "Identity (Shazam)",
                    description = "Powered by Shazam integration. Tap the button to identify a song playing around you. Once identified, our app automatically searches for it so you can play or download it instantly."
                )
            }
            item {
                InfoCard(
                    title = "Library",
                    description = "Your main music dictionary. Access all your Downloaded Songs, Liked Songs, Custom Playlists, and view your collection by Artists."
                )
            }
            item {
                InfoCard(
                    title = "Settings",
                    description = "Manage your experience. Import local songs from your phone, compress audio to save space, view app logs to understand what's happening under the hood, and check your listening stats (Top Artist & Play Count)."
                )
            }

            // --- 4. NEW FEATURES ---
            item {
                Spacer(modifier = Modifier.height(16.dp))
                InfoSectionHeader("New Features (v1.2)")
            }
            item {
                InfoCard(
                    title = "Sleep Timer",
                    description = "Fall asleep to your favorite tunes. Set a timer (from 5 to 120 minutes) in the player menu to automatically pause playback."
                )
            }
            item {
                InfoCard(
                    title = "Playback Speed Control",
                    description = "Listen at your own pace. Adjust playback speed from 0.25x to 2.0x via the player menu."
                )
            }
            item {
                InfoCard(
                    title = "Smart Shuffle Customization",
                    description = "Control how many songs are pre-loaded in Smart Shuffle mode via Settings. Higher values mean smoother playback but more data usage."
                )
            }
            item {
                InfoCard(
                    title = "Quick Queue",
                    description = "Swipe right on any song in your library to quickly add it to your playback queue."
                )
            }
            item {
                InfoCard(
                    title = "Seamless Background Playback",
                    description = "Your music continues playing seamlessly in the background. The app is deeply integrated with the Android system, allowing you to control playback directly from your notifications or lock screen."
                )
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun InfoSectionHeader(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun InfoCard(title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
