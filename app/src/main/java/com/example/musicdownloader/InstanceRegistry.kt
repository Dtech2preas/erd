package com.example.musicdownloader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

import com.example.musicdownloader.lyrics.LyricsHelper

object InstanceRegistry {

    var lyricsHelper: LyricsHelper? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS) // Short timeout for checking
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val pipedInstances = mutableListOf<String>()
    private val invidiousInstances = mutableListOf<String>()

    // Hardcoded fallbacks in case lists can't be fetched
    private val DEFAULT_PIPED = listOf(
        "https://pipedapi.kavin.rocks",
        "https://api.piped.privacy.com.de",
        "https://pipedapi.drgns.space",
        "https://api.piped.kotatsu.org",
        "https://api.piped.private.coffee"
    )

    private val DEFAULT_INVIDIOUS = listOf(
        "https://inv.nadeko.net",
        "https://invidious.f5.si",
        "https://yewtu.be",
        "https://invidious.nerdvpn.de"
    )

    // Cache for working instance to avoid re-checking every time
    private var cachedPipedInstance: String? = null
    private var cachedInvidiousInstance: String? = null

    suspend fun getWorkingPipedInstance(): String {
        cachedPipedInstance?.let { return it }

        if (pipedInstances.isEmpty()) {
            fetchPipedInstances()
        }

        // Add defaults if empty
        if (pipedInstances.isEmpty()) {
            pipedInstances.addAll(DEFAULT_PIPED)
        }

        val working = raceInstances(pipedInstances, "/streams/Mq86e4Fhja0") // Check a known video
        if (working != null) {
            cachedPipedInstance = working
            return working
        }

        throw IOException("No working Piped instance found")
    }

    suspend fun getWorkingInvidiousInstance(): String {
        cachedInvidiousInstance?.let { return it }

        if (invidiousInstances.isEmpty()) {
            fetchInvidiousInstances()
        }

        if (invidiousInstances.isEmpty()) {
            invidiousInstances.addAll(DEFAULT_INVIDIOUS)
        }

        val working = raceInstances(invidiousInstances, "/api/v1/videos/Mq86e4Fhja0")
        if (working != null) {
            cachedInvidiousInstance = working
            return working
        }
         throw IOException("No working Invidious instance found")
    }

    private suspend fun fetchPipedInstances() {
        try {
            // This is a known list of piped instances
             val request = Request.Builder().url("https://piped-instances.kavin.rocks").build()
             val responseString = withContext(Dispatchers.IO) {
                 try {
                     client.newCall(request).execute().body?.string()
                 } catch (e: Exception) {
                     null
                 }
             }

             if (responseString != null) {
                 val jsonArray = JSONArray(responseString)
                 for (i in 0 until jsonArray.length()) {
                     val obj = jsonArray.optJSONObject(i)
                     val url = obj?.optString("api_url")
                     if (!url.isNullOrBlank()) {
                         pipedInstances.add(url)
                     }
                 }
             }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun fetchInvidiousInstances() {
        try {
            val request = Request.Builder().url("https://api.invidious.io/instances.json").build()
            val responseString = withContext(Dispatchers.IO) {
                 try {
                     client.newCall(request).execute().body?.string()
                 } catch (e: Exception) {
                     null
                 }
             }

            if (responseString != null) {
                val jsonArray = JSONArray(responseString)
                for (i in 0 until jsonArray.length()) {
                    val entry = jsonArray.optJSONArray(i)
                    if (entry != null && entry.length() >= 2) {
                        val url = entry.getString(0)
                        val info = entry.getJSONObject(1)
                        val type = info.optString("type")
                        val api = info.optBoolean("api")

                        // We only want https and api enabled
                        if (type == "https" && api) {
                             // Note: Invidious list usually gives domain, we ensure protocol
                             val fullUrl = if (url.startsWith("http")) url else "https://$url"
                             invidiousInstances.add(fullUrl)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun raceInstances(instances: List<String>, testPath: String): String? = withContext(Dispatchers.IO) {
        val candidates = instances.shuffled().take(5)

        if (candidates.isEmpty()) return@withContext null

        // Use a channel to get the first successful result
        val resultChannel = Channel<String>(Channel.CONFLATED)
        val failures = AtomicInteger(0)

        coroutineScope {
            candidates.forEach { baseUrl ->
                launch {
                    val isWorking = checkInstance(baseUrl, testPath)
                    if (isWorking) {
                        resultChannel.trySend(baseUrl)
                    } else {
                        if (failures.incrementAndGet() == candidates.size) {
                            resultChannel.close()
                        }
                    }
                }
            }

            val winner = try {
                resultChannel.receive()
            } catch (e: Exception) {
                null
            }

            // Cancel other ongoing checks
            coroutineContext.cancelChildren()

            winner
        }
    }

    private fun checkInstance(baseUrl: String, testPath: String): Boolean {
        try {
            val request = Request.Builder().url("$baseUrl$testPath").head().build()
            client.newCall(request).execute().use { response ->
                // Piped returns 200 or 500 sometimes but if it connects it's better than nothing.
                // Strictly 200 is best.
                return response.isSuccessful
            }
        } catch (e: Exception) {
            return false
        }
    }
}
