package com.example.musicdownloader.utils

import android.content.Context
import android.util.Log
import com.example.musicdownloader.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object RemoteConfigClient {
    private const val TAG = "RemoteConfigClient"
    private const val CONFIG_URL = "https://dtech2preas.github.io/Mus/config.json"

    private var lastFmApiKey: String? = null
    private var lastFmBaseUrl: String = "https://ws.audioscrobbler.com/2.0/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun initConfig(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(CONFIG_URL)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string()
                        if (!bodyString.isNullOrEmpty()) {
                            val json = JSONObject(bodyString)
                            lastFmApiKey = json.optString("lastFmApiKey", null)
                            val newBaseUrl = json.optString("lastFmBaseUrl", null)
                            if (!newBaseUrl.isNullOrEmpty()) {
                                lastFmBaseUrl = newBaseUrl
                            }
                            AppLogger.log("[$TAG] Config loaded successfully. API Key present: ${lastFmApiKey != null}")

                            // Save to SharedPreferences for offline use later
                            val prefs = context.getSharedPreferences("RemoteConfig", Context.MODE_PRIVATE)
                            prefs.edit().apply {
                                putString("lastFmApiKey", lastFmApiKey)
                                putString("lastFmBaseUrl", lastFmBaseUrl)
                                apply()
                            }
                        }
                    } else {
                        AppLogger.log("[$TAG] Failed to load config: HTTP ${response.code}")
                        loadFromPrefs(context)
                    }
                }
            } catch (e: Exception) {
                AppLogger.log("[$TAG] Exception loading config: ${e.message}")
                loadFromPrefs(context)
            }
        }
    }

    private fun loadFromPrefs(context: Context) {
        val prefs = context.getSharedPreferences("RemoteConfig", Context.MODE_PRIVATE)
        lastFmApiKey = prefs.getString("lastFmApiKey", null)
        lastFmBaseUrl = prefs.getString("lastFmBaseUrl", "https://ws.audioscrobbler.com/2.0/") ?: "https://ws.audioscrobbler.com/2.0/"
        AppLogger.log("[$TAG] Loaded config from cache. API Key present: ${lastFmApiKey != null}")
    }

    fun getLastFmApiKey(): String? = lastFmApiKey
    fun getLastFmBaseUrl(): String = lastFmBaseUrl
}
