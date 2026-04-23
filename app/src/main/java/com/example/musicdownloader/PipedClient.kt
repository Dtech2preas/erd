package com.example.musicdownloader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object PipedClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .dns(IPv4Dns)
        .build()

    // We no longer use Piped for search, but keep the method signature in case we need it as fallback.
    // InnerTube is now primary for search.
    suspend fun search(query: String): List<VideoItem> {
        // Implementation preserved but unused in new plan usually
        return emptyList()
    }

    suspend fun getStreamUrl(videoId: String): String = withContext(Dispatchers.IO) {
        // Try Piped
        try {
            val pipedInstance = InstanceRegistry.getWorkingPipedInstance()
            return@withContext getStreamUrlFromPiped(pipedInstance, videoId)
        } catch (e: Exception) {
            e.printStackTrace()
            // Try Invidious if Piped fails
            try {
                val invidiousInstance = InstanceRegistry.getWorkingInvidiousInstance()
                return@withContext getStreamUrlFromInvidious(invidiousInstance, videoId)
            } catch (e2: Exception) {
                e2.printStackTrace()
                throw IOException("Both Piped and Invidious failed")
            }
        }
    }

    private fun getStreamUrlFromPiped(baseUrl: String, videoId: String): String {
        val url = "$baseUrl/streams/$videoId"
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", NetworkUtils.USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Piped error $response")

            val jsonString = response.body?.string() ?: throw IOException("Empty response")
            val json = JSONObject(jsonString)
            val audioStreams = json.optJSONArray("audioStreams") ?: throw IOException("No audio streams found")

            // Find best m4a stream
            var bestUrl = ""
            var maxBitrate = -1

            for (i in 0 until audioStreams.length()) {
                val stream = audioStreams.optJSONObject(i) ?: continue
                val mimeType = stream.optString("mimeType") // e.g. "audio/mp4"
                val bitrate = stream.optInt("bitrate", 0)
                val streamUrl = stream.optString("url")

                // Prefer m4a/mp4
                if (mimeType.contains("mp4") || mimeType.contains("m4a")) {
                    if (bitrate > maxBitrate) {
                        maxBitrate = bitrate
                        bestUrl = streamUrl
                    }
                }
            }

            // Fallback to any audio if no mp4 found
            if (bestUrl.isEmpty() && audioStreams.length() > 0) {
                 val stream = audioStreams.getJSONObject(0)
                 bestUrl = stream.optString("url")
            }

            if (bestUrl.isEmpty()) throw IOException("No valid audio stream url found")
            return bestUrl
        }
    }

    private fun getStreamUrlFromInvidious(baseUrl: String, videoId: String): String {
        val url = "$baseUrl/api/v1/videos/$videoId"
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", NetworkUtils.USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Invidious error $response")

            val jsonString = response.body?.string() ?: throw IOException("Empty response")
            val json = JSONObject(jsonString)

            // Invidious structure: "formatStreams" or "adaptiveFormats"
            // We want audio. adaptiveFormats usually has separate audio/video
            val adaptiveFormats = json.optJSONArray("adaptiveFormats")

            var bestUrl = ""
            var maxBitrate = -1

            if (adaptiveFormats != null) {
                for (i in 0 until adaptiveFormats.length()) {
                    val stream = adaptiveFormats.optJSONObject(i) ?: continue
                    val type = stream.optString("type") // e.g. "audio/mp4; codecs=\"mp4a.40.2\""
                    // bitrate is sometimes string or int in Invidious, use optString then parse
                    val bitrateStr = stream.optString("bitrate")
                    val bitrate = bitrateStr.toIntOrNull() ?: 0
                    val streamUrl = stream.optString("url")

                    if (type.contains("audio")) {
                         if (type.contains("mp4") || type.contains("m4a")) {
                             if (bitrate > maxBitrate) {
                                 maxBitrate = bitrate
                                 bestUrl = streamUrl
                             }
                         }
                    }
                }
            }

            if (bestUrl.isNotEmpty()) return bestUrl

            // Fallback to formatStreams (muxed) if no adaptive audio found (rare for music)
            val formatStreams = json.optJSONArray("formatStreams")
            if (formatStreams != null && formatStreams.length() > 0) {
                 // Just pick the first one that has audio
                 return formatStreams.getJSONObject(0).getString("url")
            }

            throw IOException("No audio stream found in Invidious")
        }
    }
}
