package com.example.musicdownloader.lyrics

import android.content.Context
import com.example.musicdownloader.data.AppDatabase
import com.example.musicdownloader.data.LyricsEntity
import com.example.musicdownloader.data.LyricsEntity.Companion.LYRICS_NOT_FOUND
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LyricsHelper(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val lyricsDao = database.lyricsDao()

    private val lyricsProviders = listOf(LrcLibLyricsProvider, KuGouLyricsProvider)

    suspend fun getLyrics(id: String, title: String, artist: String, durationMs: Long): String = withContext(Dispatchers.IO) {
        // 1. Check Database
        val cachedEntity = lyricsDao.getLyrics(id)
        if (cachedEntity != null) {
            return@withContext cachedEntity.lyrics
        }

        // Duration is usually in seconds for LrcLib API
        val durationSec = (durationMs / 1000).toInt()

        // 2. Try Providers sequentially
        for (provider in lyricsProviders) {
            if (provider.isEnabled(context)) {
                val result = provider.getLyrics(id, title, artist, durationSec)
                if (result.isSuccess) {
                    val lyrics = result.getOrNull()
                    if (!lyrics.isNullOrBlank()) {
                        // Save to DB
                        lyricsDao.insert(LyricsEntity(id, lyrics))
                        return@withContext lyrics
                    }
                }
            }
        }

        // 3. Mark as not found to avoid repeatedly fetching
        lyricsDao.insert(LyricsEntity(id, LYRICS_NOT_FOUND))
        return@withContext LYRICS_NOT_FOUND
    }
}
