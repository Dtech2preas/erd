package com.example.musicdownloader.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.musicdownloader.utils.HapticUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableSongRow(
    onSwipeToQueue: suspend () -> Boolean,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // We use rememberUpdatedState to ensure the lambda captures the latest props
    val currentOnSwipeToQueue by rememberUpdatedState(onSwipeToQueue)

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.StartToEnd) {
                scope.launch {
                    HapticUtils.performHapticFeedback(context)
                    currentOnSwipeToQueue()
                }
                // Return false to prevent the item from being dismissed (removed)
                // It will snap back to the original position
                false
            } else {
                false
            }
        }
    )

    val progress = dismissState.progress
    val alpha by animateFloatAsState(targetValue = if (progress > 0.1f) 1f else 0f, label = "icon_alpha")

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF7D5FFF)) // Electric Purple
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.alpha(alpha)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Queue",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Queue",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        enableDismissFromEndToStart = false, // Disable swipe from right to left
        content = {
            Box(modifier = Modifier.background(Color(0xFF0F0F13))) {
                content()
            }
        }
    )
}
