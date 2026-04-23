package com.example.musicdownloader

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object UserPreferences {
    private const val PREF_NAME = "user_prefs"
    private const val KEY_IS_FIRST_RUN = "is_first_run"
    private const val KEY_GENRES = "saved_genres"
    private const val KEY_ARTISTS = "saved_artists"
    private const val KEY_LAST_REFRESHED = "last_genre_refreshed"
    private const val KEY_THEME_COLOR = "theme_color"
    private const val KEY_SMART_SHUFFLE_BUFFER = "smart_shuffle_buffer_size"
    private const val KEY_HIGH_END_MODE = "high_end_mode_enabled"

    // Ad System Keys
    private const val KEY_FIRST_OPEN_TIME = "first_open_time"
    private const val KEY_DAILY_DOWNLOADS = "daily_downloads_count"
    private const val KEY_LAST_DOWNLOAD_DATE = "last_download_date"
    private const val KEY_LAST_AD_SHOWN_DATE = "last_ad_shown_date"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun isFirstRun(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_FIRST_RUN, true)
    }

    fun setFirstRunCompleted(context: Context) {
        getPrefs(context).edit {
            putBoolean(KEY_IS_FIRST_RUN, false)
        }
    }

    fun getGenres(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_GENRES, emptySet()) ?: emptySet()
    }

    fun saveGenres(context: Context, genres: Set<String>) {
        getPrefs(context).edit {
            putStringSet(KEY_GENRES, genres)
        }
    }

    fun addGenre(context: Context, genre: String) {
        val current = getGenres(context).toMutableSet()
        current.add(genre)
        saveGenres(context, current)
    }

    fun removeGenre(context: Context, genre: String) {
        val current = getGenres(context).toMutableSet()
        current.remove(genre)
        saveGenres(context, current)
    }

    fun getArtists(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_ARTISTS, emptySet()) ?: emptySet()
    }

    fun saveArtists(context: Context, artists: Set<String>) {
        getPrefs(context).edit {
            putStringSet(KEY_ARTISTS, artists)
        }
    }

    fun addArtist(context: Context, artist: String) {
        val current = getArtists(context).toMutableSet()
        current.add(artist)
        saveArtists(context, current)
    }

    fun removeArtist(context: Context, artist: String) {
        val current = getArtists(context).toMutableSet()
        current.remove(artist)
        saveArtists(context, current)
    }

    fun getLastGenreRefreshTime(context: Context): Long {
        return getPrefs(context).getLong(KEY_LAST_REFRESHED, 0L)
    }

    fun setLastGenreRefreshTime(context: Context, timestamp: Long) {
        getPrefs(context).edit {
            putLong(KEY_LAST_REFRESHED, timestamp)
        }
    }

    fun getThemeColor(context: Context): Long {
        // Default to D-TECH Blue (0xFF2962FF)
        return getPrefs(context).getLong(KEY_THEME_COLOR, 0xFF2962FF)
    }

    fun setThemeColor(context: Context, color: Long) {
        getPrefs(context).edit {
            putLong(KEY_THEME_COLOR, color)
        }
    }

    fun getSmartShuffleBuffer(context: Context): Int {
        // Default buffer size of 3 songs
        return getPrefs(context).getInt(KEY_SMART_SHUFFLE_BUFFER, 3)
    }

    fun setSmartShuffleBuffer(context: Context, size: Int) {
        getPrefs(context).edit {
            putInt(KEY_SMART_SHUFFLE_BUFFER, size.coerceIn(1, 10))
        }
    }

    fun isHighEndModeEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_HIGH_END_MODE, false)
    }

    fun setHighEndModeEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit {
            putBoolean(KEY_HIGH_END_MODE, enabled)
        }
    }

    // --- Ad System Methods ---

    fun getFirstOpenTime(context: Context): Long {
        var time = getPrefs(context).getLong(KEY_FIRST_OPEN_TIME, 0L)
        if (time == 0L) {
            // If missing (existing user), initialize to now
            time = System.currentTimeMillis()
            getPrefs(context).edit { putLong(KEY_FIRST_OPEN_TIME, time) }
        }
        return time
    }

    fun getDailyDownloadCount(context: Context): Int {
        val prefs = getPrefs(context)
        val lastDate = prefs.getString(KEY_LAST_DOWNLOAD_DATE, "")
        val today = getTodayDate()

        return if (lastDate == today) {
            prefs.getInt(KEY_DAILY_DOWNLOADS, 0)
        } else {
            0
        }
    }

    fun incrementDailyDownloadCount(context: Context) {
        val prefs = getPrefs(context)
        val today = getTodayDate()
        val lastDate = prefs.getString(KEY_LAST_DOWNLOAD_DATE, "")

        var count = if (lastDate == today) {
            prefs.getInt(KEY_DAILY_DOWNLOADS, 0)
        } else {
            0
        }

        count++

        prefs.edit {
            putString(KEY_LAST_DOWNLOAD_DATE, today)
            putInt(KEY_DAILY_DOWNLOADS, count)
        }
    }

    fun isAdShownToday(context: Context): Boolean {
        val lastShown = getPrefs(context).getString(KEY_LAST_AD_SHOWN_DATE, "")
        return lastShown == getTodayDate()
    }

    fun setAdShownToday(context: Context) {
        getPrefs(context).edit {
            putString(KEY_LAST_AD_SHOWN_DATE, getTodayDate())
        }
    }

    private fun getTodayDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
}
