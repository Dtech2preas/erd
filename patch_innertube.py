import re

with open("app/src/main/java/com/example/musicdownloader/InnerTubeClient.kt", "r") as f:
    content = f.read()

# 1. Add Playlist search
search_func_re = r'(suspend fun search\(query: String\): List<VideoItem> = withContext\(Dispatchers.IO\) \{.*?)(\n    suspend fun getStreamUrl)'
search_playlist_str = """
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
"""

def replacer(match):
    return match.group(1) + search_playlist_str + match.group(2)

new_content = re.sub(search_func_re, replacer, content, flags=re.DOTALL)


# 2. Add parser functions
parser_re = r'(private fun parseInnerTubeResponse\(json: JSONObject\): List<VideoItem> \{.*?\n    \})(\n)'
parsers_str = """
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
                                playlists.add(PlaylistItem(id = playlistId, title = title, thumbnailUrl = thumbnailUrl, songCountText = videoCountText))
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
                            val thumbnails = playlistRenderer.optJSONObject("thumbnails")?.optJSONArray(0)?.optJSONArray("thumbnails")
                            val thumbnailUrl = thumbnails?.optJSONObject(0)?.optString("url") ?: ""
                            val videoCount = playlistRenderer.optString("videoCount")

                            if (playlistId.isNotEmpty()) {
                                playlists.add(PlaylistItem(id = playlistId, title = title, thumbnailUrl = thumbnailUrl, songCountText = "$videoCount videos"))
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
"""

def parser_replacer(match):
    return match.group(1) + "\n" + parsers_str + match.group(2)

new_content = re.sub(parser_re, parser_replacer, new_content, flags=re.DOTALL)

with open("app/src/main/java/com/example/musicdownloader/InnerTubeClient.kt", "w") as f:
    f.write(new_content)
