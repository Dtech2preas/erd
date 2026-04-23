package com.example.musicdownloader

import android.content.Context
import android.content.SharedPreferences
import java.io.File

object CookieManager {
    private const val PREF_NAME = "cookie_prefs"
    private const val KEY_COOKIE = "YOUTUBE_COOKIE"
    private const val COOKIE_FILENAME = "cookies.txt"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun saveCookie(context: Context, cookieContent: String) {
        val trimmedContent = cookieContent.trim()
        val isNetscape = trimmedContent.contains("\t") && trimmedContent.lines().any { it.split("\t").size >= 7 }

        if (isNetscape) {
            // 1. Save raw content to file for yt-dlp
            saveCookieToFile(context, trimmedContent)

            // 2. Parse to header string for InnerTube/ExoPlayer
            val headerString = parseNetscapeCookiesToHeader(trimmedContent)
            getPrefs(context).edit().putString(KEY_COOKIE, headerString).apply()
        } else {
            // Assume it's already a header string (Key=Value; ...)
            // Remove the file so yt-dlp doesn't use old cookies
            val file = File(context.filesDir, COOKIE_FILENAME)
            if (file.exists()) {
                file.delete()
            }

            getPrefs(context).edit().putString(KEY_COOKIE, trimmedContent).apply()
        }
    }

    fun getCookie(context: Context): String {
        return getPrefs(context).getString(KEY_COOKIE, "") ?: ""
    }

    fun getCookieFile(context: Context): File? {
        val file = File(context.filesDir, COOKIE_FILENAME)
        return if (file.exists()) file else null
    }

    fun checkAndLogCookies(context: Context) {
        val cookie = getCookie(context)
        if (cookie.isEmpty()) {
            AppLogger.log("[CookieManager] No cookies found in preferences.")
            return
        }

        val keys = cookie.split(";").map { it.substringBefore("=").trim() }
        // Common important YouTube cookies
        val importantKeys = listOf("SAPISID", "__Secure-3PSID", "LOGIN_INFO", "VISITOR_INFO1_LIVE")
        val foundKeys = keys.filter { key -> importantKeys.any { it.equals(key, ignoreCase = true) } }

        AppLogger.log("[CookieManager] Cookie Check: Found ${keys.size} cookies. Important present: $foundKeys")
    }

    private fun saveCookieToFile(context: Context, content: String) {
        try {
            val file = File(context.filesDir, COOKIE_FILENAME)
            file.writeText(content)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parseNetscapeCookiesToHeader(netscapeContent: String): String {
        val sb = StringBuilder()
        val lines = netscapeContent.lines()
        for (line in lines) {
            if (line.isBlank() || line.startsWith("#")) continue

            val parts = line.split("\t")
            if (parts.size >= 7) {
                // Netscape format: domain, flag, path, secure, expiration, name, value
                val name = parts[5]
                val value = parts[6]
                if (sb.isNotEmpty()) {
                    sb.append("; ")
                }
                sb.append("$name=$value")
            }
        }
        return sb.toString()
    }
}
