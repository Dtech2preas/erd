package com.example.musicdownloader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CookieDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var cookieText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set YouTube Cookie") },
        text = {
            Column {
                Text("Paste your cookies.txt content or Netscape formatted cookies here.")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = cookieText,
                    onValueChange = { cookieText = it },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    placeholder = { Text("Cookie content...") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(cookieText) }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
