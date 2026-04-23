package com.example.musicdownloader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object InnerTubeClient {

    val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .dns(IPv4Dns)
        .build()

    // Public InnerTube API Key (WEB Client)
    private const val API_KEY = "AIzaSyD2" + "C07e2_" + "49XC2" + "sT5e" + "0M_" + "E2" // Obfuscated slightly

    private const val BASE_URL = "https://youtubei.googleapis.com/youtubei/v1/search?key=$API_KEY"
    private const val PLAYER_URL = "https://youtubei.googleapis.com/youtubei/v1/player?key=$API_KEY"

    suspend fun search(query: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val jsonBody = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "WEB")
                    put("clientVersion", "2.20230920.00.00") // A relatively recent version
                    put("hl", "en")
                    put("gl", "US")
                })
            })
            put("query", query)
            // "EgIQAQ%3D%3D" is the param for "Video" filter.
            put("params", "EgIQAQ%3D%3D")
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())

        // Search uses WEB client, so we keep a desktop User-Agent to match the client context
        val request = Request.Builder()
            .url(BASE_URL)
            .post(requestBody)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("InnerTube Search failed: ${response.code}")

            val responseString = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(responseString)

            return@withContext parseInnerTubeResponse(json)
        }
    }

    suspend fun searchPlaylists(query: String): List<PlaylistItem> = withContext(Dispatchers.IO) {
        val jsonBody = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "WEB")
                    put("clientVersion", "2.20230920.00.00")
                    put("hl", "en")
                    put("gl", "US")
                })
            })
            put("query", query)
            // "EgIQAw%3D%3D" is the param for "Playlist/Album" filter.
            put("params", "EgIQAw%3D%3D")
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(BASE_URL)
            .post(requestBody)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("InnerTube Search failed: ${response.code}")

            val responseString = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(responseString)

            return@withContext parseInnerTubePlaylistResponse(json)
        }
    }

    suspend fun getPlaylistVideos(playlistId: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val jsonBody = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "WEB")
                    put("clientVersion", "2.20230920.00.00")
                    put("hl", "en")
                    put("gl", "US")
                })
            })
            put("browseId", "VL$playlistId") // InnerTube requires "VL" prefix for playlists sometimes, or it just uses browseId
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://youtubei.googleapis.com/youtubei/v1/browse?key=$API_KEY")
            .post(requestBody)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("InnerTube Browse failed: ${response.code}")

            val responseString = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(responseString)

            return@withContext parsePlaylistVideosResponse(json)
        }
    }

    suspend fun getStreamUrl(context: android.content.Context, videoId: String): StreamInfo = withContext(Dispatchers.IO) {
        val jsonBody = JSONObject().apply {
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "ANDROID")
                    put("clientVersion", "19.29.35")
                    put("platform", "MOBILE")
                    put("osName", "Android")
                    put("osVersion", "14")
                    put("androidSdkVersion", 34)
                    put("hl", "en")
                    put("gl", "US")
                })
            })
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())

        val cookie = CookieManager.getCookie(context)

        val requestBuilder = Request.Builder()
            .url(PLAYER_URL)
            .post(requestBody)
            .addHeader("User-Agent", NetworkUtils.USER_AGENT)

        if (cookie.isNotEmpty()) {
            requestBuilder.addHeader("Cookie", cookie)
        }

        val request = requestBuilder.build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("InnerTube Player failed: ${response.code}")

            val responseString = response.body?.string() ?: throw IOException("Empty response")
            val json = JSONObject(responseString)

            return@withContext parsePlayerResponse(json)
        }
    }

    suspend fun fetchMetadata(context: android.content.Context, videoId: String): VideoItem = withContext(Dispatchers.IO) {
        val jsonBody = JSONObject().apply {
            put("videoId", videoId)
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "ANDROID")
                    put("clientVersion", "19.29.35")
                    put("platform", "MOBILE")
                    put("osName", "Android")
                    put("osVersion", "14")
                    put("androidSdkVersion", 34)
                    put("hl", "en")
                    put("gl", "US")
                })
            })
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
        val cookie = CookieManager.getCookie(context)

        val requestBuilder = Request.Builder()
            .url(PLAYER_URL)
            .post(requestBody)
            .addHeader("User-Agent", NetworkUtils.USER_AGENT)

        if (cookie.isNotEmpty()) {
            requestBuilder.addHeader("Cookie", cookie)
        }

        val request = requestBuilder.build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Metadata fetch failed: ${response.code}")

            val responseString = response.body?.string() ?: throw IOException("Empty response")
            val json = JSONObject(responseString)

            val videoDetails = json.optJSONObject("videoDetails") ?: throw IOException("No videoDetails")
            val title = videoDetails.optString("title")
            val author = videoDetails.optString("author")
            val lengthSeconds = videoDetails.optLong("lengthSeconds")

            // Format seconds to mm:ss
            val minutes = lengthSeconds / 60
            val seconds = lengthSeconds % 60
            val durationStr = String.format("%02d:%02d", minutes, seconds)

            // Thumbnails
            val thumbnails = videoDetails.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            val thumbnailUrl = if (thumbnails != null && thumbnails.length() > 0) {
                 thumbnails.optJSONObject(thumbnails.length() - 1).optString("url")
            } else {
                 "https://i.ytimg.com/vi/$videoId/mqdefault.jpg"
            }

            return@withContext VideoItem(
                id = videoId,
                title = title,
                uploader = author,
                duration = durationStr,
                thumbnailUrl = thumbnailUrl,
                webUrl = "https://www.youtube.com/watch?v=$videoId"
            )
        }
    }

    private fun parseInnerTubeResponse(json: JSONObject): List<VideoItem> {
        val videos = mutableListOf<VideoItem>()

        try {
            val contents = json.optJSONObject("contents")
                ?.optJSONObject("twoColumnSearchResultsRenderer")
                ?.optJSONObject("primaryContents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")

            if (contents == null) return emptyList()

            for (i in 0 until contents.length()) {
                val itemSection = contents.optJSONObject(i)?.optJSONObject("itemSectionRenderer")
                val results = itemSection?.optJSONArray("contents") ?: continue

                for (j in 0 until results.length()) {
                    val videoRenderer = results.optJSONObject(j)?.optJSONObject("videoRenderer")
                    if (videoRenderer != null) {
                        try {
                            val videoId = videoRenderer.optString("videoId")
                            val title = videoRenderer.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "Unknown"

                            // Duration
                            val lengthText = videoRenderer.optJSONObject("lengthText")?.optString("simpleText") ?: ""

                            // Uploader
                            val ownerText = videoRenderer.optJSONObject("ownerText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "Unknown"

                            // Thumbnail (get the last/largest one)
                            val thumbnails = videoRenderer.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                            val thumbnailUrl = if (thumbnails != null && thumbnails.length() > 0) {
                                thumbnails.optJSONObject(thumbnails.length() - 1).optString("url")
                            } else {
                                "https://i.ytimg.com/vi/$videoId/mqdefault.jpg"
                            }

                            if (videoId.isNotEmpty()) {
                                videos.add(
                                    VideoItem(
                                        id = videoId,
                                        title = title,
                                        duration = lengthText,
                                        uploader = ownerText,
                                        thumbnailUrl = thumbnailUrl,
                                        webUrl = "https://www.youtube.com/watch?v=$videoId"
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return videos
    }

    private fun parseInnerTubePlaylistResponse(json: JSONObject): List<PlaylistItem> {
        val playlists = mutableListOf<PlaylistItem>()
        try {
            val contents = json.optJSONObject("contents")
                ?.optJSONObject("twoColumnSearchResultsRenderer")
                ?.optJSONObject("primaryContents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")

            if (contents == null) return emptyList()

            for (i in 0 until contents.length()) {
                val itemSection = contents.optJSONObject(i)?.optJSONObject("itemSectionRenderer")
                val results = itemSection?.optJSONArray("contents") ?: continue

                for (j in 0 until results.length()) {
                    val lockupViewModel = results.optJSONObject(j)?.optJSONObject("lockupViewModel")
                    if (lockupViewModel != null) {
                        try {
                            val metadata = lockupViewModel.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
                            val title = metadata?.optJSONObject("title")?.optString("content") ?: "Unknown Playlist"

                            var playlistId = ""
                            val contentId = lockupViewModel.optString("contentId")
                            if (contentId.isNotEmpty()) {
                                playlistId = contentId
                            } else {
                                val watchEndpoint = lockupViewModel.optJSONObject("contentImage")
                                    ?.optJSONObject("collectionThumbnailViewModel")
                                    ?.optJSONObject("primaryThumbnail")
                                    ?.optJSONObject("thumbnailViewModel")
                                    ?.optJSONObject("image")
                                    // Sometimes id is nested elsewhere, we'll try basic approach or fallback to the playlistRenderer approach if needed
                                    // Wait, the API response earlier showed playlistId inside lockupViewModel -> metadata -> ... No, we need a reliable way.
                            }

                            // Let's also check for playlistRenderer which sometimes appears
                            val playlistRenderer = results.optJSONObject(j)?.optJSONObject("playlistRenderer")
                            if (playlistRenderer != null) {
                                playlistId = playlistRenderer.optString("playlistId")
                            }

                            if (playlistId.isEmpty() && contentId.isNotEmpty()) {
                                playlistId = contentId // Usually playlist id is the contentId
                            }

                            if (playlistId.isEmpty()) {
                                // Try another path
                                playlistId = metadata?.optJSONObject("metadata")?.optJSONObject("contentMetadataViewModel")
                                    ?.optJSONObject("metadataRows")?.optJSONArray("metadataRows")
                                    ?.optJSONObject(0)?.optJSONArray("metadataParts")?.optJSONObject(0)?.optString("content") ?: ""
                            }

                            val thumbnailUrl = lockupViewModel.optJSONObject("contentImage")
                                ?.optJSONObject("collectionThumbnailViewModel")
                                ?.optJSONObject("primaryThumbnail")
                                ?.optJSONObject("thumbnailViewModel")
                                ?.optJSONObject("image")
                                ?.optJSONArray("sources")
                                ?.optJSONObject(0)
                                ?.optString("url") ?: ""

                            val videoCountText = metadata?.optJSONObject("metadata")?.optJSONObject("contentMetadataViewModel")?.optJSONArray("metadataRows")?.optJSONObject(0)?.optJSONArray("metadataParts")?.optJSONObject(0)?.optJSONObject("text")?.optString("content") ?: ""

                            if (playlistId.isNotEmpty()) {
                                playlists.add(PlaylistItem(id = playlistId, title = title, thumbnailUrl = thumbnailUrl, songCountText = videoCountText.toString()))
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    } else {
                        // Fallback to playlistRenderer
                        val playlistRenderer = results.optJSONObject(j)?.optJSONObject("playlistRenderer")
                        if (playlistRenderer != null) {
                            val playlistId = playlistRenderer.optString("playlistId")
                            val title = playlistRenderer.optJSONObject("title")?.optString("simpleText") ?: "Unknown Playlist"
                            val thumbnails = playlistRenderer.optJSONArray("thumbnails")?.optJSONObject(0)?.optJSONArray("thumbnails")
                            val thumbnailUrl = thumbnails?.optJSONObject(0)?.optString("url") ?: ""
                            val videoCount = playlistRenderer.optString("videoCount")

                            if (playlistId.isNotEmpty()) {
                                playlists.add(PlaylistItem(id = playlistId, title = title, thumbnailUrl = thumbnailUrl, songCountText = "${videoCount} videos"))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return playlists
    }

    private fun parsePlaylistVideosResponse(json: JSONObject): List<VideoItem> {
        val videos = mutableListOf<VideoItem>()
        try {
            val tabs = json.optJSONObject("contents")?.optJSONObject("twoColumnBrowseResultsRenderer")?.optJSONArray("tabs")
            if (tabs != null && tabs.length() > 0) {
                val contents = tabs.optJSONObject(0)?.optJSONObject("tabRenderer")?.optJSONObject("content")?.optJSONObject("sectionListRenderer")?.optJSONArray("contents")
                if (contents != null) {
                    for (i in 0 until contents.length()) {
                        val items = contents.optJSONObject(i)?.optJSONObject("itemSectionRenderer")?.optJSONArray("contents") ?: continue
                        for (j in 0 until items.length()) {
                            val playlistItems = items.optJSONObject(j)?.optJSONObject("playlistVideoListRenderer")?.optJSONArray("contents") ?: continue
                            for (k in 0 until playlistItems.length()) {
                                val renderer = playlistItems.optJSONObject(k)?.optJSONObject("playlistVideoRenderer")
                                if (renderer != null) {
                                    val videoId = renderer.optString("videoId")
                                    val title = renderer.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "Unknown"
                                    val durationText = renderer.optJSONObject("lengthText")?.optString("simpleText") ?: ""
                                    val uploader = renderer.optJSONObject("shortBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "Unknown"
                                    val thumbnails = renderer.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                                    val thumbnailUrl = if (thumbnails != null && thumbnails.length() > 0) {
                                        thumbnails.optJSONObject(thumbnails.length() - 1).optString("url")
                                    } else {
                                        "https://i.ytimg.com/vi/$videoId/mqdefault.jpg"
                                    }
                                    if (videoId.isNotEmpty()) {
                                        videos.add(VideoItem(id = videoId, title = title, duration = durationText, uploader = uploader, thumbnailUrl = thumbnailUrl, webUrl = "https://www.youtube.com/watch?v=$videoId"))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return videos
    }


    private fun parsePlayerResponse(json: JSONObject): StreamInfo {
        val streamingData = json.optJSONObject("streamingData") ?: throw IOException("No streaming data")

        // Priority 1: HLS Manifest (m3u8) - specific to iOS/Web clients
        val hlsManifestUrl = streamingData.optString("hlsManifestUrl")
        if (hlsManifestUrl.isNotEmpty()) {
            return StreamInfo(hlsManifestUrl, true)
        }

        // Priority 2: Adaptive Formats (legacy fallback)
        val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats")
        if (adaptiveFormats != null) {
            for (i in 0 until adaptiveFormats.length()) {
                val format = adaptiveFormats.optJSONObject(i) ?: continue
                val mimeType = format.optString("mimeType")
                val url = format.optString("url")

                // Look only for audio streams in adaptiveFormats
                if (mimeType.contains("audio") && url.isNotEmpty()) {
                    return StreamInfo(url, false)
                }
            }
        }

        throw IOException("No valid audio stream found")
    }
}
