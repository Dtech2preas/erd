package com.example.musicdownloader

import android.app.Application
import android.widget.Toast
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.example.musicdownloader.workers.StreamRefresherWorker
import com.example.musicdownloader.utils.RemoteConfigClient
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

class MusicDownloaderApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        // Force IPv4 globally to avoid IPv6 latency issues
        System.setProperty("java.net.preferIPv4Stack", "true")

        try {
            YoutubeDL.getInstance().init(this)
        } catch (e: YoutubeDLException) {
            e.printStackTrace()
            // In a real app, you might want to show a UI error if init fails
            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(applicationContext, "Failed to initialize YoutubeDL: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        scheduleStreamRefresher()
        CoroutineScope(Dispatchers.IO).launch { RemoteConfigClient.initConfig(this@MusicDownloaderApp) }
        InstanceRegistry.lyricsHelper = com.example.musicdownloader.lyrics.LyricsHelper(this)
    }

    private fun scheduleStreamRefresher() {
        // Schedule periodic stream refresh (every 15 minutes)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicRequest = PeriodicWorkRequestBuilder<StreamRefresherWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .addTag("stream_refresher_periodic")
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "StreamRefresherPeriodic",
            ExistingPeriodicWorkPolicy.KEEP, // Keep existing if already scheduled
            periodicRequest
        )
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .networkCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .build()
    }
}
