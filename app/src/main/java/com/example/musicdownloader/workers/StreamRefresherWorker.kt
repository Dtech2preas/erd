package com.example.musicdownloader.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.musicdownloader.AppLogger
import com.example.musicdownloader.MusicRepository
import com.example.musicdownloader.UserPreferences
import com.example.musicdownloader.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

class StreamRefresherWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        AppLogger.log("[StreamRefresher] Worker started.")

        val context = applicationContext
        val isHighEndMode = UserPreferences.isHighEndModeEnabled(context)
        val database = AppDatabase.getDatabase(context)
        val streamSongDao = database.streamSongDao()
        val streamCacheDao = database.streamCacheDao()

        // 1. Clean up expired cache entries first
        val currentTime = System.currentTimeMillis() / 1000
        streamCacheDao.clearExpired(currentTime)

        // 2. Decide if we should run heavy operations
        if (!isHighEndMode) {
             AppLogger.log("[StreamRefresher] High-End Mode is OFF. Skipping aggressive refresh.")
             return Result.success()
        }

        // 3. Fetch all stream songs (Library + Recommended)
        val allStreamSongs = withContext(Dispatchers.IO) {
             streamSongDao.getAllStreamSongs().firstOrNull() ?: emptyList()
        }

        if (allStreamSongs.isEmpty()) {
            AppLogger.log("[StreamRefresher] No stream songs found.")
            return Result.success()
        }

        // 4. Filter candidates
        val candidates = allStreamSongs

        AppLogger.log("[StreamRefresher] Found ${candidates.size} candidates for refresh (HighEnd: ON).")

        // 5. Check Cache Status
        val songsToRefresh = mutableListOf<String>()
        val expiredRecommendations = mutableListOf<String>()

        for (song in candidates) {
            val cached = streamCacheDao.getStreamCache(song.id)
            if (cached == null) {
                // Missing cache -> Refresh
                songsToRefresh.add(song.id)
            } else {
                // Has cache, check expiry
                if (cached.expireTime - currentTime < 600) {
                     if (!song.isManual) {
                         // Expired Recommended -> Mark for rotation
                         expiredRecommendations.add(song.id)
                     } else {
                         // Expired Library -> Refresh
                         songsToRefresh.add(song.id)
                     }
                }
            }
        }

        // 6. Handle Expired Recommendations (Rotation)
        if (expiredRecommendations.isNotEmpty()) {
            AppLogger.log("[StreamRefresher] Rotating ${expiredRecommendations.size} expired recommendations individually...")
            expiredRecommendations.forEach { id ->
                streamSongDao.deleteById(id)
                streamCacheDao.deleteStreamCache(id)
                // Fetch a single replacement to maintain the list size without wiping everything
                MusicRepository.fetchSingleRecommendation(context)
            }
        }

        // 7. Batch Fetch URLs (Concurrency Limit: 7, Stagger: 5s)
        if (songsToRefresh.isNotEmpty()) {
            AppLogger.log("[StreamRefresher] refreshing ${songsToRefresh.size} URLs...")
            processBatch(context, songsToRefresh)
        }

        AppLogger.log("[StreamRefresher] Worker finished.")
        return Result.success()
    }

    private suspend fun processBatch(context: Context, videoIds: List<String>) = coroutineScope {
        val maxConcurrent = 7
        val staggerDelay = 5000L // 5 seconds
        val activeJobs = AtomicInteger(0)

        val jobs = mutableListOf<kotlinx.coroutines.Job>()

        for (id in videoIds) {
            // Wait if max concurrent reached
            while (activeJobs.get() >= maxConcurrent) {
                delay(1000)
            }

            activeJobs.incrementAndGet()
            val job = async(Dispatchers.IO) {
                try {
                    AppLogger.log("[StreamRefresher] Fetching URL for $id")
                    val webUrl = "https://www.youtube.com/watch?v=$id"
                    MusicRepository.getStreamUrlWithCache(context, id, webUrl)
                } catch (e: Exception) {
                    AppLogger.log("[StreamRefresher] Failed to fetch $id: ${e.message}")
                } finally {
                    activeJobs.decrementAndGet()
                }
            }
            jobs.add(job)

            // Stagger next launch
            delay(staggerDelay)
        }

        jobs.joinAll()
    }
}
