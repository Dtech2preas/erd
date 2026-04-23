package com.example.musicdownloader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.musicdownloader.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

// Updated Premium Palette
val DeepBlue = Color(0xFF050510) // Midnight Black
val SurfaceBlue = Color(0xFF12121A) // Dark Glass
val DTechBlue = Color(0xFF2962FF) // Royal Blue
val PremiumGold = DTechBlue // Replaced Gold with Blue as requested
val ElectricPurple = DTechBlue // Alias for backward compatibility, but now Blue
val CyanAccent = PremiumGold // Update accent to Gold
val TextWhite = Color(0xFFFFFFFF)
val TextGray = Color(0xFFB0B0B0)

// Helper to observe theme changes
object ThemeManager {
    private val _themeColor = MutableStateFlow(DTechBlue)
    val themeColor = _themeColor.asStateFlow()

    fun updateTheme(color: Long) {
        _themeColor.value = Color(color)
    }
}

@Composable
fun MusicAppTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val dynamicColor = ThemeManager.themeColor.collectAsState()

    LaunchedEffect(Unit) {
        // Load saved theme on startup
        val savedColor = UserPreferences.getThemeColor(context)
        // If saved color is the old default purple, migrate to new blue?
        // Or just let it be. If they never changed it, it might return a default.
        // UserPreferences.getThemeColor likely returns a default if not found.
        // We can't easily check "if default" without knowing the implementation of getThemeColor.
        // But if they customized it, we respect it.
        ThemeManager.updateTheme(savedColor)
    }

    val colorScheme = remember(dynamicColor.value) {
        darkColorScheme(
            primary = dynamicColor.value,
            secondary = PremiumGold,
            background = DeepBlue,
            surface = SurfaceBlue,
            onPrimary = Color.White,
            onSecondary = Color.Black,
            onBackground = TextWhite,
            onSurface = TextWhite,
            surfaceVariant = SurfaceBlue,
            onSurfaceVariant = TextWhite,
            tertiary = PremiumGold // Use Gold for tertiary accents too
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
