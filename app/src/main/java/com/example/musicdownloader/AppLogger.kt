package com.example.musicdownloader

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs = _logs.asStateFlow()

    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun log(message: String) {
        val timestamp = dateFormat.format(Date())
        val logEntry = "[$timestamp] $message"

        // Update state in a thread-safe way (though simple assignment to value is safe for StateFlow in this context usually,
        // appending to a list needs care. We create a new list).
        // Since this might be called from multiple threads, synchronized block or similar might be good,
        // but StateFlow value update is thread-safe. We just need to ensure we don't lose concurrent updates if possible,
        // but for logs, last-write-wins or just replacing list is okay.
        // Better: use a lock or simple synchronized method.
        synchronized(this) {
            val currentList = _logs.value.toMutableList()
            currentList.add(logEntry)
            _logs.value = currentList
        }

        // Also print to Android Logcat
        android.util.Log.d("AppLogger", logEntry)
    }

    fun exportLogs(context: Context): String {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(downloadsDir, "music_downloader_logs_$timestamp.txt")

            file.writeText(_logs.value.joinToString("\n"))
            "Saved to ${file.absolutePath}"
        } catch (e: Exception) {
            e.printStackTrace()
            "Failed to export: ${e.message}"
        }
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
