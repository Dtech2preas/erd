package com.example.musicdownloader.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.random.Random

@Composable
fun RealtimeVisualizer(
    audioSessionId: Int, // Kept for compatibility
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 24,
    barColor: Color = ElectricPurple
) {
    // State to hold current height multipliers (0.0 - 1.0)
    val heights = remember { mutableStateListOf<Float>().apply { repeat(barCount) { add(0.1f) } } }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isActive) {
                // Randomize heights
                for (i in 0 until barCount) {
                    // Generate a random target height
                    val target = Random.nextFloat().coerceIn(0.1f, 1.0f)
                    heights[i] = target
                }
                // Update every 80-150ms to simulate beat
                delay(Random.nextLong(80, 150))
            }
        } else {
            // Reset to idle
            for (i in 0 until barCount) {
                heights[i] = 0.1f
            }
        }
    }

    Canvas(modifier = modifier) {
        val widthPerBar = size.width / barCount.toFloat()
        val gap = widthPerBar * 0.2f
        val barWidth = widthPerBar - gap

        for (i in 0 until barCount) {
            // Safety check
            if (i >= heights.size) break

            val fraction = heights[i]
            val barHeight = size.height * fraction

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(barColor, barColor.copy(alpha = 0.5f))
                ),
                topLeft = Offset(i * widthPerBar, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(4.dp.toPx())
            )
        }
    }
}
