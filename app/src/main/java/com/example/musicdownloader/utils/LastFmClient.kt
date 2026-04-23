package com.example.musicdownloader.utils

import com.example.musicdownloader.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class LastFmTrack(
    val name: String,
    val artist: String
)

object LastFmClient {
    private const val TAG = "LastFmClient"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun getSimilarTracks(trackName: String, artistName: String): List<LastFmTrack> = withContext(Dispatchers.IO) {
        val apiKey = RemoteConfigClient.getLastFmApiKey()
        if (apiKey.isNullOrEmpty()) {
            AppLogger.log("[$TAG] API key is missing. Cannot fetch similar tracks.")
            return@withContext emptyList()
        }

        val baseUrl = RemoteConfigClient.getLastFmBaseUrl()
        val urlBuilder = baseUrl.toHttpUrlOrNull()?.newBuilder()
        if (urlBuilder == null) {
            AppLogger.log("[$TAG] Invalid base URL: $baseUrl")
            return@withContext emptyList()
        }

        urlBuilder.addQueryParameter("method", "track.getsimilar")
        urlBuilder.addQueryParameter("artist", artistName)
        urlBuilder.addQueryParameter("track", trackName)
        urlBuilder.addQueryParameter("api_key", apiKey)
        urlBuilder.addQueryParameter("format", "json")
        urlBuilder.addQueryParameter("limit", "50")

        val request = Request.Builder()
            .url(urlBuilder.build())
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.log("[$TAG] HTTP Error: ${response.code}")
                    return@withContext emptyList()
                }

                val bodyString = response.body?.string()
                if (bodyString.isNullOrEmpty()) {
                    return@withContext emptyList()
                }

                val json = JSONObject(bodyString)
                val similartracks = json.optJSONObject("similartracks")
                val trackArray = similartracks?.optJSONArray("track")

                if (trackArray == null) {
                     AppLogger.log("[$TAG] No similar tracks found in JSON.")
                     return@withContext emptyList()
                }

                val result = mutableListOf<LastFmTrack>()
                for (i in 0 until trackArray.length()) {
                    val trackObj = trackArray.getJSONObject(i)
                    val name = trackObj.optString("name")
                    val artistObj = trackObj.optJSONObject("artist")
                    val artist = artistObj?.optString("name") ?: ""

                    if (name.isNotEmpty() && artist.isNotEmpty()) {
                        result.add(LastFmTrack(name, artist))
                    }
                }

                AppLogger.log("[$TAG] Found ${result.size} similar tracks for $trackName - $artistName")
                return@withContext result
            }
        } catch (e: Exception) {
            AppLogger.log("[$TAG] Exception fetching similar tracks: ${e.message}")
            return@withContext emptyList()
        }
    }
}
