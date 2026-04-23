package com.example.musicdownloader.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.musicdownloader.AppLogger
import com.example.musicdownloader.YoutubeClient
import com.example.musicdownloader.data.AppDatabase
import com.example.musicdownloader.data.Song
import java.io.File

class MusicDownloadWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val videoId = inputData.getString("videoId") ?: return Result.failure()
        val title = inputData.getString("title") ?: "Unknown Title"
        val artist = inputData.getString("artist") ?: "Unknown Artist"
        val thumbnailUrl = inputData.getString("thumbnailUrl") ?: ""
        val duration = inputData.getString("duration") ?: ""
        val album = inputData.getString("album") ?: "Unknown Album"

        val context = applicationContext
        val outputDir = File(context.filesDir, "music_downloads")
        if (!outputDir.exists()) outputDir.mkdirs()

        AppLogger.log("[Worker] Starting download for $title ($videoId)")

        // Report progress so UI can see the title
        setProgress(workDataOf("title" to title))

        return try {
            val file = YoutubeClient.downloadAudio(context, videoId, title, outputDir)

            // Insert into Database
            val database = AppDatabase.getDatabase(context)
            val song = Song(
                id = videoId,
                title = title,
                artist = artist,
                thumbnailUrl = thumbnailUrl,
                filePath = file.absolutePath,
                duration = duration,
                album = album
            )
            database.songDao().insert(song)

            AppLogger.log("[Worker] Download success & DB inserted: $title")

            // Return output data so we can maybe notify UI if needed
            val outputData = workDataOf("filePath" to file.absolutePath)
            Result.success(outputData)
        } catch (e: Exception) {
            AppLogger.log("[Worker] Download failed: ${e.message}")
            Result.failure()
        }
    }
}
