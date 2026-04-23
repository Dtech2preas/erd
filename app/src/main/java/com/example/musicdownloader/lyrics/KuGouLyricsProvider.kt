package com.example.musicdownloader.lyrics

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException
import android.util.Base64

object KuGouLyricsProvider : LyricsProvider {
    override val name = "KuGou"

    override fun isEnabled(context: Context): Boolean = true // Always enabled for now

    private val client = OkHttpClient()

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 1. Search Song
            val searchUrl = okhttp3.HttpUrl.Builder()
                .scheme("https")
                .host("mobileservice.kugou.com")
                .addPathSegment("api")
                .addPathSegment("v3")
                .addPathSegment("search")
                .addPathSegment("song")
                .addQueryParameter("version", "9108")
                .addQueryParameter("plat", "0")
                .addQueryParameter("pagesize", "8")
                .addQueryParameter("showtype", "0")
                .addQueryParameter("keyword", "$artist - $title")
                .build()

            val searchRequest = Request.Builder().url(searchUrl).build()
            val searchResponse = client.newCall(searchRequest).execute()
            if (!searchResponse.isSuccessful) {
                return@withContext Result.failure(IOException("KuGou search failed"))
            }

            val searchBody = searchResponse.body?.string() ?: return@withContext Result.failure(Exception("Empty search body"))
            val searchJson = JSONObject(searchBody)
            val data = searchJson.optJSONObject("data") ?: return@withContext Result.failure(Exception("No data in KuGou search"))
            val info = data.optJSONArray("info") ?: return@withContext Result.failure(Exception("No info in KuGou search"))

            if (info.length() == 0) {
                return@withContext Result.failure(Exception("KuGou search returned empty"))
            }

            val firstSong = info.getJSONObject(0)
            val hash = firstSong.optString("hash")
            if (hash.isEmpty()) {
                return@withContext Result.failure(Exception("KuGou song has no hash"))
            }

            // 2. Search Lyrics Candidates
            val lyricsSearchUrl = okhttp3.HttpUrl.Builder()
                .scheme("https")
                .host("lyrics.kugou.com")
                .addPathSegment("search")
                .addQueryParameter("ver", "1")
                .addQueryParameter("man", "yes")
                .addQueryParameter("client", "pc")
                .addQueryParameter("keyword", "$artist - $title")
                .addQueryParameter("hash", hash)
                // Duration is expected in ms here usually, we'll pass if we have it
                .apply {
                    if (duration > 0) {
                        addQueryParameter("duration", (duration * 1000).toString()) // Assume input is seconds
                    }
                }
                .build()

            val lyricsSearchRequest = Request.Builder().url(lyricsSearchUrl).build()
            val lyricsSearchResponse = client.newCall(lyricsSearchRequest).execute()
            if (!lyricsSearchResponse.isSuccessful) return@withContext Result.failure(Exception("KuGou lyrics search failed"))

            val lyricsSearchBody = lyricsSearchResponse.body?.string() ?: return@withContext Result.failure(Exception("Empty lyrics search body"))
            val lyricsSearchJson = JSONObject(lyricsSearchBody)
            val candidates = lyricsSearchJson.optJSONArray("candidates") ?: return@withContext Result.failure(Exception("No candidates found"))

            if (candidates.length() == 0) return@withContext Result.failure(Exception("KuGou lyrics candidates empty"))

            val firstCandidate = candidates.getJSONObject(0)
            val lyricsId = firstCandidate.optString("id")
            val accessKey = firstCandidate.optString("accesskey")

            // 3. Download Lyrics
            val downloadUrl = okhttp3.HttpUrl.Builder()
                .scheme("https")
                .host("lyrics.kugou.com")
                .addPathSegment("download")
                .addQueryParameter("ver", "1")
                .addQueryParameter("client", "pc")
                .addQueryParameter("id", lyricsId)
                .addQueryParameter("accesskey", accessKey)
                .addQueryParameter("fmt", "lrc")
                .addQueryParameter("charset", "utf8")
                .build()

            val downloadRequest = Request.Builder().url(downloadUrl).build()
            val downloadResponse = client.newCall(downloadRequest).execute()
            if (!downloadResponse.isSuccessful) return@withContext Result.failure(Exception("KuGou lyrics download failed"))

            val downloadBody = downloadResponse.body?.string() ?: return@withContext Result.failure(Exception("Empty lyrics download body"))
            val downloadJson = JSONObject(downloadBody)
            val base64Lyrics = downloadJson.optString("content")

            if (base64Lyrics.isEmpty()) return@withContext Result.failure(Exception("No base64 content"))

            val decodedLyricsBytes = Base64.decode(base64Lyrics, Base64.DEFAULT)
            val decodedLyrics = String(decodedLyricsBytes, Charsets.UTF_8)

            Result.success(decodedLyrics)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
