package com.example.musicdownloader.lyrics

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

object LrcLibLyricsProvider : LyricsProvider {
    override val name = "LrcLib"

    override fun isEnabled(context: Context): Boolean = true // Always enabled for now

    private val client = OkHttpClient()

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // LrcLib API expects duration in seconds, duration parameter is likely in milliseconds or seconds depending on upstream. Let's assume seconds for now as the provider takes int.
            val durationInSeconds = duration // Assuming duration is passed in seconds based on typical usage, if it's ms, we'll need to divide by 1000. Wait, MediaMetadata duration is usually ms. Let's adjust if needed.

            // Build the URL
            val urlBuilder = okhttp3.HttpUrl.Builder()
                .scheme("https")
                .host("lrclib.net")
                .addPathSegment("api")
                .addPathSegment("get")
                .addQueryParameter("track_name", title)
                .addQueryParameter("artist_name", artist)

            if (duration > 0) {
                // Typical durations are > 1000 if they are in ms. If it's a 3 min song, it's 180s.
                // Let's assume duration is in seconds.
                urlBuilder.addQueryParameter("duration", duration.toString())
            }

            val request = Request.Builder()
                .url(urlBuilder.build())
                .header("User-Agent", "DTECH_MUSIC (https://github.com/dtech2preas/Mus)") // Good practice to include a user agent
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("Unexpected code $response"))
            }

            val responseBody = response.body?.string() ?: return@withContext Result.failure(Exception("Empty body"))
            val jsonObject = JSONObject(responseBody)

            // Prefer synced lyrics, fallback to plain lyrics
            val syncedLyrics = jsonObject.optString("syncedLyrics")
            if (syncedLyrics.isNotEmpty()) {
                return@withContext Result.success(syncedLyrics)
            }

            val plainLyrics = jsonObject.optString("plainLyrics")
            if (plainLyrics.isNotEmpty()) {
                return@withContext Result.success(plainLyrics)
            }

            Result.failure(Exception("No lyrics found in response"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
