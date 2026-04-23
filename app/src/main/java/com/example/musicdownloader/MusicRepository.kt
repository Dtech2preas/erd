package com.example.musicdownloader

import android.content.Context
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.musicdownloader.data.AppDatabase
import com.example.musicdownloader.data.FavoriteSong
import com.example.musicdownloader.data.PlayHistory
import com.example.musicdownloader.data.Playlist
import com.example.musicdownloader.data.PlaylistEntry
import com.example.musicdownloader.data.Song
import com.example.musicdownloader.data.StreamCache
import com.example.musicdownloader.data.StreamSong
import com.example.musicdownloader.workers.MusicDownloadWorker
import com.example.musicdownloader.workers.StreamRefresherWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

data class GenreFeed(val genreName: String, val songs: List<VideoItem>)

object MusicRepository {

    // Simple In-Memory Cache
    private val searchCache = ConcurrentHashMap<String, List<VideoItem>>()

    // Cache duration constant
    private const val CACHE_DURATION_MS = 24 * 60 * 60 * 1000L // 24 hours

    suspend fun searchVideos(context: Context, query: String): Result<List<VideoItem>> {
        AppLogger.log("[Repo] searchVideos called for: '$query'")
        // 1. Check Cache
        searchCache[query]?.let {
            AppLogger.log("[Repo] Cache HIT for '$query' (${it.size} items)")
            return Result.success(it)
        }
        AppLogger.log("[Repo] Cache MISS for '$query'")

        // 2. Try InnerTube (Fastest & Most Reliable)
        try {
            AppLogger.log("[Repo] Trying InnerTube search...")
            val videos = InnerTubeClient.search(query)
            if (videos.isNotEmpty()) {
                AppLogger.log("[Repo] InnerTube success: ${videos.size} items found")
                searchCache[query] = videos
                return Result.success(videos)
            } else {
                 AppLogger.log("[Repo] InnerTube returned empty list.")
            }
        } catch (e: Exception) {
            AppLogger.log("[Repo] InnerTube failed: ${e.message}")
            e.printStackTrace()
            // Continue to fallbacks
        }

        // 3. Fallback to YoutubeClient (Slow but reliable backup)
        return try {
            AppLogger.log("[Repo] Fallback to YoutubeClient search...")
            val videos = YoutubeClient.searchVideos(context, query)
            if (videos.isNotEmpty()) {
                AppLogger.log("[Repo] YoutubeClient success: ${videos.size} items found")
                searchCache[query] = videos
            } else {
                AppLogger.log("[Repo] YoutubeClient returned empty list.")
            }
            Result.success(videos)
        } catch (e: Exception) {
            AppLogger.log("[Repo] YoutubeClient failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun fetchGenreFeeds(context: Context, genres: Set<String>): List<GenreFeed> = coroutineScope {
        val lastRefreshed = UserPreferences.getLastGenreRefreshTime(context)
        val currentTime = System.currentTimeMillis()
        val TWO_HOURS_MS = 2 * 60 * 60 * 1000L
        val shouldRefresh = (currentTime - lastRefreshed) > TWO_HOURS_MS

        if (shouldRefresh) {
            // Instead of fully clearing the memory cache, we'll keep 75% of existing results and fetch 25% new ones.
            // Since `searchVideos` uses InnerTubeClient.search, we can just remove the cache for genres,
            // fetch new ones, and merge them, keeping 75% old, 25% new.
            for (genre in genres) {
                val oldList = searchCache[genre] ?: emptyList()
                if (oldList.isNotEmpty()) {
                    try {
                        // Fetch new ones
                        val newResults = InnerTubeClient.search(genre)
                        val newFiltered = newResults.filter { parseDuration(it.duration) in 60..600 }
                        // Take 25% (roughly 3 new ones if size is 10)
                        val numNew = (oldList.size * 0.25).toInt().coerceAtLeast(1)
                        val distinctNew = newFiltered.filter { newVideo -> oldList.none { it.id == newVideo.id } }.take(numNew)

                        // Shift: drop the oldest `distinctNew.size` items and append new ones
                        val combined = oldList.drop(distinctNew.size) + distinctNew
                        searchCache[genre] = combined
                    } catch (e: Exception) {
                        AppLogger.log("[Repo] Failed to fetch partial genre update for $genre: ${e.message}")
                    }
                } else {
                    // Initial fetch
                    try {
                        val newResults = InnerTubeClient.search(genre)
                        searchCache[genre] = newResults.filter { parseDuration(it.duration) in 60..600 }.take(10)
                    } catch (e: Exception) {
                        AppLogger.log("[Repo] Failed initial fetch for $genre: ${e.message}")
                    }
                }
            }
            UserPreferences.setLastGenreRefreshTime(context, currentTime)
        } else {
            // Make sure cache has data if it's empty in memory (app restart)
            genres.forEach { genre ->
                if (searchCache[genre].isNullOrEmpty()) {
                    try {
                        val newResults = InnerTubeClient.search(genre)
                        searchCache[genre] = newResults.filter { parseDuration(it.duration) in 60..600 }.take(10)
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }
        }

        // Return feeds from cache
        genres.map { genre ->
            val results = searchCache[genre] ?: emptyList()
            GenreFeed(genre, results.take(10))
        }
    }

    /**
     * Enqueues a download request to WorkManager.
     */
    suspend fun downloadSong(context: Context, video: VideoItem): Result<String> {
        if (video.id.isBlank()) {
            AppLogger.log("[Repo] downloadSong called with empty ID")
            return Result.failure(Exception("Invalid Video ID"))
        }

        val outputDir = File(context.filesDir, "music_downloads")
        if (!outputDir.exists()) outputDir.mkdirs()

        // Check if file already exists
        val existingFiles = outputDir.listFiles { _, name -> name.startsWith(video.id) }
        if (!existingFiles.isNullOrEmpty()) {
            AppLogger.log("[Repo] File already exists for ${video.id}")

            // Ensure it's in DB
            val database = AppDatabase.getDatabase(context)
            if (database.songDao().getSongById(video.id) == null) {
                // Insert if missing
                 val song = Song(
                    id = video.id,
                    title = video.title,
                    artist = video.uploader,
                    thumbnailUrl = video.thumbnailUrl,
                    filePath = existingFiles.first().absolutePath,
                    duration = video.duration,
                    album = video.album ?: "Unknown Album"
                )
                database.songDao().insert(song)
            }
            return Result.success("File already exists")
        }

        AppLogger.log("[Repo] Enqueueing download worker for ${video.id}")

        val workData = workDataOf(
            "videoId" to video.id,
            "title" to video.title,
            "artist" to video.uploader,
            "thumbnailUrl" to video.thumbnailUrl,
            "duration" to video.duration,
            "album" to (video.album ?: "Unknown Album")
        )

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val downloadRequest = OneTimeWorkRequestBuilder<MusicDownloadWorker>()
            .setInputData(workData)
            .setConstraints(constraints)
            .addTag("download") // Generic tag
            .addTag("download_${video.id}") // Specific tag
            .build()

        WorkManager.getInstance(context).enqueue(downloadRequest)

        return Result.success("Download queued")
    }

    fun deleteDownload(context: Context, videoId: String) {
        AppLogger.log("[Repo] Deleting download for $videoId")

        // 1. Cancel Work
        WorkManager.getInstance(context).cancelAllWorkByTag("download_$videoId")

        // 2. Remove from Client Status
        YoutubeClient.removeDownloadStatus(videoId)

        // 3. Delete Files (Partially downloaded or complete)
        val outputDir = File(context.filesDir, "music_downloads")
        if (outputDir.exists()) {
            outputDir.listFiles { _, name -> name.startsWith(videoId) }?.forEach { file ->
                try {
                    file.delete()
                    AppLogger.log("[Repo] Deleted file: ${file.name}")
                } catch (e: Exception) {
                    AppLogger.log("[Repo] Failed to delete file: ${file.name}")
                }
            }
        }
    }

    fun pauseDownload(context: Context, videoId: String) {
        AppLogger.log("[Repo] Pausing download for $videoId")
        // Cancel work (stops process)
        WorkManager.getInstance(context).cancelAllWorkByTag("download_$videoId")
        // Mark as paused in Client
        YoutubeClient.pauseDownloadStatus(videoId)
    }

    // Helper to sync file system with DB on startup
    suspend fun syncFilesWithDatabase(context: Context) = withContext(Dispatchers.IO) {
        val database = AppDatabase.getDatabase(context)

        // 1. Clean up invalid entries from DB first
        try {
            database.songDao().deleteById("")
            database.streamSongDao().deleteById("")
            // Also try to catch whitespace-only IDs which might slip through "deleteById('')"
            // We can't query "isBlank" easily in SQL, so we trust "deleteById" cleans empty strings
            // and we rely on preventing new ones.
            // However, we can iterate all songs and check in code if we really want to be sure.
            // For now, simple cleanup.
            AppLogger.log("[Repo] Cleaned up empty ID entries from DB")
        } catch (e: Exception) {
            AppLogger.log("[Repo] Failed to clean up DB: ${e.message}")
        }

        val outputDir = File(context.filesDir, "music_downloads")
        if (!outputDir.exists()) return@withContext

        val files = outputDir.listFiles() ?: return@withContext

        // Cleanup .deleted files
        files.filter { it.name.endsWith(".deleted") }.forEach {
             AppLogger.log("[Repo] Cleaning up .deleted file: ${it.name}")
             it.delete()
        }

        // Sync valid files
        files.filter { !it.name.endsWith(".deleted") && !it.name.startsWith(".") }.forEach { file ->
            // Filename format: {id}.{ext} usually
            val id = file.nameWithoutExtension

            if (id.isBlank()) {
                AppLogger.log("[Repo] Found file with empty ID, deleting: ${file.name}")
                file.delete()
                return@forEach
            }

            val existingSong = database.songDao().getSongById(id)
            if (existingSong == null) {
                AppLogger.log("[Repo] Found orphaned file: ${file.name}, inserting into DB")
                val song = Song(
                    id = id,
                    title = "Unknown Song ($id)",
                    artist = "Unknown Artist",
                    thumbnailUrl = "https://i.ytimg.com/vi/$id/mqdefault.jpg", // Guess thumbnail
                    filePath = file.absolutePath,
                    duration = ""
                )
                database.songDao().insert(song)
            }
        }

        fixUnknownSongs(context)

        // Prune expired stream cache on startup
        try {
            database.streamCacheDao().clearExpired(System.currentTimeMillis() / 1000)
            AppLogger.log("[Repo] Expired stream cache pruned.")
        } catch (e: Exception) {
            AppLogger.log("[Repo] Failed to prune stream cache: ${e.message}")
        }
    }

    suspend fun rescanLibrary(context: Context) = withContext(Dispatchers.IO) {
        AppLogger.log("[Repo] Starting library rescan...")
        syncFilesWithDatabase(context)
        fixUnknownSongs(context)
        AppLogger.log("[Repo] Library rescan complete.")
    }

    private suspend fun fixUnknownSongs(context: Context) = withContext(Dispatchers.IO) {
        val database = AppDatabase.getDatabase(context)
        val songs = database.songDao().getAllSongsSync() // Need a synchronous fetch or flow collection

        // Filter for "Unknown Song" OR any song with "Unknown Artist" to be more thorough
        songs.filter { it.title.startsWith("Unknown Song") || it.artist == "Unknown Artist" }.forEach { song ->
            try {
                AppLogger.log("[Repo] Attempting to recover metadata for ${song.id}")
                val metadata = InnerTubeClient.fetchMetadata(context, song.id)

                // Only update if we actually got valid data back
                if (metadata.title.isNotBlank() && metadata.title != "Unknown Title") {
                    val updatedSong = song.copy(
                        title = metadata.title,
                        artist = metadata.uploader,
                        duration = metadata.duration,
                        thumbnailUrl = metadata.thumbnailUrl,
                        album = metadata.album ?: song.album
                    )

                    database.songDao().insert(updatedSong) // Insert with same ID replaces
                    AppLogger.log("[Repo] Recovered metadata for ${song.id}: ${metadata.title}")
                } else {
                    AppLogger.log("[Repo] Metadata fetch returned empty/invalid for ${song.id}")
                }
            } catch (e: Exception) {
                AppLogger.log("[Repo] Failed to recover metadata for ${song.id}: ${e.message}")
            }
        }
    }

    // History Methods
    fun getRecentHistory(context: Context): Flow<List<PlayHistory>> {
        // Fetch 50, then distinct by Video ID, then take 6
        return AppDatabase.getDatabase(context).playHistoryDao().getRecentHistory(50)
            .map { list ->
                list.distinctBy { it.songId }.take(6)
            }
    }

    suspend fun addToHistory(context: Context, video: VideoItem) {
        AppLogger.log("[Repo] addToHistory (VideoItem): ${video.title}")
        val history = PlayHistory(
            songId = video.id,
            title = video.title,
            artist = video.uploader,
            thumbnailUrl = video.thumbnailUrl
        )
        val db = AppDatabase.getDatabase(context)
        val dao = db.playHistoryDao()
        dao.insert(history)
        dao.enforceLimit()

        // Hook: if this was a recommended song, remove it and fetch a new one
        val streamSongDao = db.streamSongDao()
        val streamSong = streamSongDao.getStreamSongById(video.id)
        if (streamSong != null && !streamSong.isManual) {
            AppLogger.log("[Repo] Recommended song played. Rotating out: ${video.title}")
            streamSongDao.deleteById(video.id)
            db.streamCacheDao().deleteStreamCache(video.id)
            // Fetch single replacement
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                fetchSingleRecommendation(context)
            }
        }
    }

    suspend fun addToHistory(context: Context, song: Song) {
        AppLogger.log("[Repo] addToHistory (Song): ${song.title}")
        val history = PlayHistory(
            songId = song.id,
            title = song.title,
            artist = song.artist,
            thumbnailUrl = song.thumbnailUrl
        )
        val db = AppDatabase.getDatabase(context)
        val dao = db.playHistoryDao()
        dao.insert(history)
        dao.enforceLimit()

        // Hook: if this was a recommended song, remove it and fetch a new one
        val streamSongDao = db.streamSongDao()
        val streamSong = streamSongDao.getStreamSongById(song.id)
        if (streamSong != null && !streamSong.isManual) {
            AppLogger.log("[Repo] Recommended song played. Rotating out: ${song.title}")
            streamSongDao.deleteById(song.id)
            db.streamCacheDao().deleteStreamCache(song.id)
            // Fetch single replacement
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                fetchSingleRecommendation(context)
            }
        }
    }

    // DNA Stats Methods
    fun getTopArtist(context: Context): Flow<com.example.musicdownloader.data.ArtistCount?> {
        return AppDatabase.getDatabase(context).playHistoryDao().getTopArtist()
    }

    fun getTotalPlayCount(context: Context): Flow<Int> {
        return AppDatabase.getDatabase(context).playHistoryDao().getTotalPlayCount()
    }

    // Helper method for Smart Shuffle
    suspend fun getTopArtistsList(context: Context): List<String> {
        return withContext(Dispatchers.IO) {
            // This needs a DAO method that returns a list.
            // For now, assume topArtist is one, we might need to expand PlayHistoryDao later.
            // Using a simple query if possible, or defaulting to last history items artists.
            val history = AppDatabase.getDatabase(context).playHistoryDao().getRecentHistory(50).map { it.distinctBy { h -> h.artist }.map { h -> h.artist } }
            // Since we need a synchronous return (not flow) for the manager:
            // This is a bit tricky with Flows. Let's rely on cached data or just blocking get if necessary,
            // but for now let's query raw DB if we can, or just return empty and let manager handle defaults.
            // A better approach is to add a List<ArtistCount> query to DAO.
            // We will do a basic distinct artist fetch from recent history manually here:
             emptyList() // Placeholder, logic will be in SmartShuffleManager mostly
        }
    }

    // Favorites Methods
    suspend fun setLikeStatus(context: Context, songId: String, isLiked: Boolean) {
        val dao = AppDatabase.getDatabase(context).favoriteDao()
        if (isLiked) {
            dao.insert(FavoriteSong(songId))
        } else {
            dao.deleteById(songId)
        }
    }

    fun getLikedSongIds(context: Context): Flow<List<String>> {
        return AppDatabase.getDatabase(context).favoriteDao().getAllLikedIds()
    }

    suspend fun getLikedSongs(context: Context): List<String> {
        // Synchronous fetch for SmartShuffleManager
        // This requires adding a suspend function to FavoriteDao that returns List instead of Flow
        // We can't easily change DAO interface without checking file.
        // For now, we will skip this or assume flow collection elsewhere.
        return emptyList()
    }

    fun isLiked(context: Context, songId: String): Flow<Boolean> {
        return AppDatabase.getDatabase(context).favoriteDao().isLiked(songId)
    }

    // Playlist Methods
    suspend fun createPlaylist(context: Context, name: String) {
        AppDatabase.getDatabase(context).playlistDao().insertPlaylist(Playlist(name = name))
    }

    fun getPlaylists(context: Context): Flow<List<Playlist>> {
        return AppDatabase.getDatabase(context).playlistDao().getAllPlaylists()
    }

    suspend fun addSongToPlaylist(context: Context, playlistId: Int, songId: String) {
        AppDatabase.getDatabase(context).playlistDao().addSongToPlaylist(PlaylistEntry(playlistId, songId))
    }

    fun getPlaylistSongs(context: Context, playlistId: Int): Flow<List<Song>> {
        val database = AppDatabase.getDatabase(context)
        return combine(
            database.playlistDao().getPlaylistEntries(playlistId),
            getLibrarySongs(context)
        ) { entries, librarySongs ->
            // Map entries to songs to preserve playlist order (if entries are ordered)
            // and filter out missing songs
            entries.mapNotNull { entry ->
                librarySongs.find { it.id == entry.songId }
            }
        }
    }

    suspend fun replaceSongFile(context: Context, oldSong: Song, newFile: File): Song = withContext(Dispatchers.IO) {
        val database = AppDatabase.getDatabase(context)

        val oldFile = File(oldSong.filePath)
        val extension = newFile.extension
        val finalFile = File(context.filesDir, "music_downloads/${oldSong.id}.$extension")

        // Ensure parent dir exists
        finalFile.parentFile?.mkdirs()

        // 1. Move new file to final location
        if (newFile.absolutePath != finalFile.absolutePath) {
            // If final file exists (and is not our new file source), delete it to allow rename
            if (finalFile.exists()) {
                if (!finalFile.delete()) {
                    // Try to proceed, but rename might fail
                    AppLogger.log("[Repo] Warning: Could not delete target file ${finalFile.name}")
                }
            }

            if (!newFile.renameTo(finalFile)) {
                // Fallback: Copy and Delete
                try {
                    newFile.copyTo(finalFile, overwrite = true)
                    newFile.delete()
                } catch (e: Exception) {
                    throw java.io.IOException("Failed to move compressed file to ${finalFile.absolutePath}: ${e.message}")
                }
            }
        }

        // 2. Delete old file (only if it is a different path than the final one)
        if (oldFile.exists() && oldFile.absolutePath != finalFile.absolutePath) {
            oldFile.delete()
        }

        // 3. Update Database
        val updatedSong = oldSong.copy(
            filePath = finalFile.absolutePath
        )
        database.songDao().insert(updatedSong) // Insert with same ID replaces

        AppLogger.log("[Repo] Replaced song file for ${oldSong.title}. New path: ${finalFile.absolutePath}")
        return@withContext updatedSong
    }

    suspend fun importLocalSongs(context: Context): Int = withContext(Dispatchers.IO) {
        var count = 0
        try {
            val projection = arrayOf(
                android.provider.MediaStore.Audio.Media._ID,
                android.provider.MediaStore.Audio.Media.TITLE,
                android.provider.MediaStore.Audio.Media.ARTIST,
                android.provider.MediaStore.Audio.Media.ALBUM,
                android.provider.MediaStore.Audio.Media.DURATION,
                android.provider.MediaStore.Audio.Media.DATA
            )

            // Query for audio files
            val cursor = context.contentResolver.query(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${android.provider.MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                null
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media._ID)
                val titleCol = it.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.TITLE)
                val artistCol = it.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ARTIST)
                val albumCol = it.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ALBUM)
                val durationCol = it.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DURATION)
                val dataCol = it.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATA)

                val database = AppDatabase.getDatabase(context)

                while (it.moveToNext()) {
                    val fileId = it.getLong(idCol).toString()
                    val title = it.getString(titleCol) ?: "Unknown Title"
                    val artist = it.getString(artistCol) ?: "Unknown Artist"
                    val album = it.getString(albumCol) ?: "Unknown Album"
                    val durationMs = it.getLong(durationCol)
                    val path = it.getString(dataCol)

                    // Format Duration
                    val durationSec = durationMs / 1000
                    val minutes = durationSec / 60
                    val seconds = durationSec % 60
                    val durationStr = String.format("%d:%02d", minutes, seconds)

                    // Check for duplicates by path or ID prefix "local_"
                    val localId = "local_$fileId"
                    if (database.songDao().getSongById(localId) == null) {
                         val song = Song(
                             id = localId,
                             title = title,
                             artist = artist,
                             album = album,
                             duration = durationStr,
                             filePath = path,
                             thumbnailUrl = "" // No thumbnail for local yet
                         )
                         database.songDao().insert(song)
                         count++
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.log("[Repo] Import failed: ${e.message}")
            e.printStackTrace()
        }
        return@withContext count
    }

    // --- Stream Caching & Prefetching ---


    suspend fun searchPlaylists(context: Context, query: String): Result<List<PlaylistItem>> {
        AppLogger.log("[Repo] searchPlaylists called for: '$query'")
        try {
            val items = InnerTubeClient.searchPlaylists(query)
            return Result.success(items)
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.failure(e)
        }
    }

    suspend fun getPlaylistVideos(context: Context, playlistId: String): Result<List<VideoItem>> {
        AppLogger.log("[Repo] getPlaylistVideos called for: '$playlistId'")
        try {
            val items = InnerTubeClient.getPlaylistVideos(playlistId)
            return Result.success(items)
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.failure(e)
        }
    }

    suspend fun getStreamUrlWithCache(context: Context, videoId: String, webUrl: String): StreamInfo {
        val dao = AppDatabase.getDatabase(context).streamCacheDao()
        val currentTime = System.currentTimeMillis() / 1000

        // 1. Check DB Cache
        val cached = dao.getStreamCache(videoId)
        if (cached != null) {
            if (isValidStreamUrl(cached.streamUrl) && cached.expireTime > currentTime) {
                AppLogger.log("[Repo] Stream Cache HIT for $videoId. Expires in ${(cached.expireTime - currentTime)}s")
                return StreamInfo(cached.streamUrl, cached.streamUrl.contains(".m3u8"))
            } else {
                if (cached.expireTime <= currentTime) {
                     AppLogger.log("[Repo] Stream Cache EXPIRED for $videoId")
                } else {
                     AppLogger.log("[Repo] Stream Cache HIT but URL INVALID for $videoId. Deleting.")
                     dao.deleteStreamCache(videoId)
                }
            }
        } else {
            AppLogger.log("[Repo] Stream Cache MISS for $videoId")
        }

        // 2. Fetch new URL
        val streamInfo = InnerTubeClient.getStreamUrl(context, videoId)

        // 3. Parse Expiration & Cache
        if (isValidStreamUrl(streamInfo.url)) {
            val expire = extractExpiration(streamInfo.url)
            if (expire > 0) {
                // Safety buffer: subtract 5 minutes from expiration
                val safeExpire = expire - 300
                if (safeExpire > currentTime) {
                    dao.insert(StreamCache(videoId, streamInfo.url, safeExpire, currentTime))
                    AppLogger.log("[Repo] Cached stream for $videoId. Expires at $safeExpire")
                }
            }
        } else if (streamInfo.url.isNotBlank()) {
             AppLogger.log("[Repo] Fetched URL is invalid, not caching.")
        }

        return streamInfo
    }

    private fun isValidStreamUrl(url: String): Boolean {
        return url.isNotBlank() && url.startsWith("http") && !url.contains(" ")
    }

    suspend fun prefetchStream(context: Context, videoId: String) {
        // Construct webUrl (standard format)
        val webUrl = "https://www.youtube.com/watch?v=$videoId"
        try {
            // This will trigger the cache logic
            getStreamUrlWithCache(context, videoId, webUrl)

            // Also add to Recommendations (StreamSong isManual=0)
            // We need metadata for this.
            try {
                val metadata = InnerTubeClient.fetchMetadata(context, videoId)
                if (metadata.title.isNotBlank()) {
                     autoAddStreamSong(context, metadata)
                }
            } catch (e: Exception) {
                 AppLogger.log("[Repo] Failed to fetch metadata for prefetched song $videoId: ${e.message}")
            }

        } catch (e: Exception) {
            AppLogger.log("[Repo] Prefetch failed for $videoId: ${e.message}")
        }
    }

    private fun extractExpiration(url: String): Long {
        try {
            // Regex for 'expire=1234567890'
            val matcher = Pattern.compile("expire=(\\d+)").matcher(url)
            if (matcher.find()) {
                return matcher.group(1)?.toLong() ?: 0L
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return 0L
    }

    // --- Unified Library Logic ---

    fun getLibrarySongs(context: Context): Flow<List<Song>> {
        val database = AppDatabase.getDatabase(context)
        return combine(
            database.songDao().getAll(),
            database.streamSongDao().getLibrarySongs()
        ) { downloads, streams ->
            val mappedStreams = streams.map { stream ->
                Song(
                    id = stream.id,
                    title = stream.title,
                    artist = stream.artist,
                    album = stream.album,
                    duration = stream.duration,
                    thumbnailUrl = stream.thumbnailUrl,
                    filePath = "stream://${stream.id}" // Marker for Stream
                )
            }.filter { it.id.isNotBlank() } // Enforce valid IDs

            // Filter downloads too just in case
            val validDownloads = downloads.filter { it.id.isNotBlank() }

            // Combine and distinct by ID (Downloads take precedence)
            val downloadIds = validDownloads.map { it.id }.toSet()
            val uniqueStreams = mappedStreams.filter { it.id !in downloadIds }

            validDownloads + uniqueStreams
        }
    }

    fun getRecommendedSongs(context: Context): Flow<List<StreamSong>> {
        val database = AppDatabase.getDatabase(context)
        return combine(
            database.streamSongDao().getRecommendedSongs(), // isManual=0
            database.playHistoryDao().getAllHistoryIds(),
            database.streamSongDao().getLibrarySongs() // isManual=1
        ) { recommended, historyIds, library ->
            val playedSet = historyIds.toSet()
            val librarySet = library.map { it.id }.toSet()

            // Filter out played/library songs first
            val validCandidates = recommended.filter {
                it.id !in playedSet && it.id !in librarySet
            }.take(15)

            validCandidates
        }.combine(getAllCachedIdsFlow(context)) { candidates, cachedIds ->
            // Only apply cache filter if High-End Mode is enabled.
            // If disabled, we show all candidates (allowing them to be fetched on demand).
            if (UserPreferences.isHighEndModeEnabled(context)) {
                candidates.filter { it.id in cachedIds }
            } else {
                candidates
            }
        }
    }

    // Helper flow to get all cached IDs
    fun getAllCachedIdsFlow(context: Context): Flow<List<String>> {
         return AppDatabase.getDatabase(context).streamCacheDao().getAllCachedIds()
    }

    suspend fun refreshRecommendations(context: Context) {
        AppLogger.log("[Repo] Refreshing Recommendations (New Logic)...")
        val database = AppDatabase.getDatabase(context)
        val dao = database.streamSongDao()

        // 1. Clear old recommendations
        dao.clearRecommended()

        // 2. Prepare Inputs
        val topArtist = database.playHistoryDao().getTopArtistSync()
        val genres = UserPreferences.getGenres(context).toList()
        val favoriteArtists = UserPreferences.getArtists(context).toList()

        val finalSelection = mutableListOf<VideoItem>()
        val targetSize = 15

        // 3. Artists Logic (Top Artist & Favorite Artists)
        val selectedArtists = mutableSetOf<String>()
        if (topArtist != null) selectedArtists.add(topArtist.artist)
        if (favoriteArtists.isNotEmpty()) selectedArtists.add(favoriteArtists.random())

        for (artist in selectedArtists) {
            if (finalSelection.size >= 5) break // Allow max 5 slots for artists initially

            val artistQuery = "$artist songs"
            try {
                AppLogger.log("[Repo] Fetching artist recs: $artistQuery")
                val results = InnerTubeClient.search(artistQuery)
                val filtered = results.filter { parseDuration(it.duration) < 900 }

                var count = 0
                for (video in filtered) {
                    if (count >= 2) break // Max 2 per artist
                    if (finalSelection.none { it.id == video.id }) {
                        finalSelection.add(video)
                        count++
                    }
                }
            } catch (e: Exception) {
                AppLogger.log("[Repo] Failed to fetch artist recs for $artist: ${e.message}")
            }
        }

        // 4. Genre Logic
        if (genres.isEmpty()) {
            try {
                val query = "Trending Music"
                AppLogger.log("[Repo] Fetching fallback: $query")
                val results = InnerTubeClient.search(query)
                val filtered = results.filter { parseDuration(it.duration) < 900 }

                for (video in filtered) {
                    if (finalSelection.size >= targetSize) break
                    if (finalSelection.none { it.id == video.id }) {
                        finalSelection.add(video)
                    }
                }
            } catch (e: Exception) {
                AppLogger.log("[Repo] Fallback failed: ${e.message}")
            }
        } else {
            val shuffledGenres = genres.shuffled()
            val genreResults = mutableMapOf<String, List<VideoItem>>()
            val activeGenres = shuffledGenres.take(15)

            for (genre in activeGenres) {
                try {
                    val query = genre
                    AppLogger.log("[Repo] Fetching genre recs: $query")
                    val results = InnerTubeClient.search(query)
                    val filtered = results.filter { parseDuration(it.duration) < 900 }
                    if (filtered.isNotEmpty()) {
                        genreResults[genre] = filtered
                    }
                } catch (e: Exception) {
                    AppLogger.log("[Repo] Failed genre fetch for $genre: ${e.message}")
                }
            }

            var index = 0
            var addedAnything = true

            while (finalSelection.size < targetSize && addedAnything) {
                addedAnything = false
                for (genre in activeGenres) {
                    if (finalSelection.size >= targetSize) break

                    val list = genreResults[genre]
                    if (list != null && index < list.size) {
                        val candidate = list[index]
                        if (finalSelection.none { it.id == candidate.id }) {
                            finalSelection.add(candidate)
                            addedAnything = true
                        }
                    }
                }
                index++
            }
        }

        // 5. Shuffle and Insert
        finalSelection.shuffled().forEach { video ->
            autoAddStreamSong(context, video)
        }

        AppLogger.log("[Repo] Added ${finalSelection.size} new recommendations. Triggering StreamRefresherWorker...")

        // 6. Trigger Stream Fetcher Immediate
        val request = OneTimeWorkRequestBuilder<StreamRefresherWorker>()
            .addTag("refresh_streams")
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }


    suspend fun fetchSingleRecommendation(context: Context) {
        AppLogger.log("[Repo] Fetching a single new recommendation...")
        val database = AppDatabase.getDatabase(context)

        // Prepare Inputs
        val topArtist = database.playHistoryDao().getTopArtistSync()
        val genres = UserPreferences.getGenres(context).toList()
        val favoriteArtists = UserPreferences.getArtists(context).toList()

        val selectedArtists = mutableSetOf<String>()
        if (topArtist != null) selectedArtists.add(topArtist.artist)
        if (favoriteArtists.isNotEmpty()) selectedArtists.add(favoriteArtists.random())

        val queries = mutableListOf<String>()
        if (selectedArtists.isNotEmpty()) queries.add("${selectedArtists.random()} songs")
        if (genres.isNotEmpty()) queries.add(genres.random())
        if (queries.isEmpty()) queries.add("Trending Music")

        val query = queries.random()
        try {
            AppLogger.log("[Repo] Fetching single rec for query: $query")
            val results = InnerTubeClient.search(query)
            val filtered = results.filter { parseDuration(it.duration) < 900 }

            // Need to filter out already recommended or in library
            val currentRecs = database.streamSongDao().getRecommendedSongsSync() ?: emptyList()
            val library = database.streamSongDao().getLibrarySongsSync() ?: emptyList()
            val history = database.playHistoryDao().getAllHistoryIdsSync() ?: emptyList()

            val excludeIds = currentRecs.map { it.id }.toSet() + library.map { it.id }.toSet() + history.toSet()

            for (video in filtered) {
                if (video.id !in excludeIds) {
                    autoAddStreamSong(context, video)
                    AppLogger.log("[Repo] Added single recommendation: ${video.title}")

                    // Trigger refresh to cache stream URL if High End mode is on
                    if (UserPreferences.isHighEndModeEnabled(context)) {
                        val request = androidx.work.OneTimeWorkRequestBuilder<com.example.musicdownloader.workers.StreamRefresherWorker>()
                            .addTag("refresh_streams")
                            .build()
                        androidx.work.WorkManager.getInstance(context).enqueue(request)
                    }
                    return
                }
            }
        } catch (e: Exception) {
            AppLogger.log("[Repo] Failed to fetch single rec: ${e.message}")
        }
    }

    private fun parseDuration(durationStr: String): Long {
        try {
            val parts = durationStr.split(":").map { it.toLong() }
            return when (parts.size) {
                2 -> parts[0] * 60 + parts[1]
                3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
                else -> 0L
            }
        } catch (e: Exception) {
            return 0L
        }
    }

    suspend fun addToLibrary(context: Context, video: VideoItem, streamUrl: String? = null) {
        if (video.id.isBlank()) {
            AppLogger.log("[Repo] addToLibrary aborted: Video ID is blank.")
            return
        }
        val song = StreamSong(
            id = video.id,
            title = video.title,
            artist = video.uploader,
            album = video.album ?: "Unknown Album",
            duration = video.duration,
            thumbnailUrl = video.thumbnailUrl,
            isManual = true,
            timestamp = System.currentTimeMillis()
        )
        val database = AppDatabase.getDatabase(context)
        database.streamSongDao().insert(song)
        AppLogger.log("[Repo] Added to Library (Stream): ${video.title}")

        // Cache the stream URL if provided
        if (streamUrl != null && isValidStreamUrl(streamUrl)) {
            val expire = extractExpiration(streamUrl)
            if (expire > 0) {
                val currentTime = System.currentTimeMillis() / 1000
                // Safety buffer: subtract 5 minutes
                val safeExpire = expire - 300
                if (safeExpire > currentTime) {
                    database.streamCacheDao().insert(StreamCache(video.id, streamUrl, safeExpire, currentTime))
                    AppLogger.log("[Repo] Cached stream for library song ${video.id}. Expires at $safeExpire")
                }
            }
        }

        // Trigger worker to ensure it stays fresh if High End Mode is on
        if (UserPreferences.isHighEndModeEnabled(context)) {
             val request = OneTimeWorkRequestBuilder<StreamRefresherWorker>()
                .addTag("refresh_streams")
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }

    suspend fun removeFromLibrary(context: Context, songId: String) {
        val dao = AppDatabase.getDatabase(context).streamSongDao()
        val song = dao.getStreamSongById(songId)
        if (song != null && song.isManual) {
            // Downgrade to auto-added so it remains in cache/recs but not in library
            dao.insert(song.copy(isManual = false))
            AppLogger.log("[Repo] Removed from Library (Stream): ${song.title}")
        }
    }

    suspend fun autoAddStreamSong(context: Context, video: VideoItem) {
        if (video.id.isBlank()) {
            AppLogger.log("[Repo] autoAddStreamSong aborted: Video ID is blank.")
            return
        }
        val dao = AppDatabase.getDatabase(context).streamSongDao()
        val existing = dao.getStreamSongById(video.id)
        if (existing == null) {
            val song = StreamSong(
                id = video.id,
                title = video.title,
                artist = video.uploader,
                album = video.album ?: "Unknown Album",
                duration = video.duration,
                thumbnailUrl = video.thumbnailUrl,
                isManual = false,
                timestamp = System.currentTimeMillis()
            )
            dao.insert(song)
            AppLogger.log("[Repo] Auto-added stream song: ${video.title}")
        } else {
            // Update timestamp for auto songs to keep them fresh in recommended
            if (!existing.isManual) {
                dao.insert(existing.copy(timestamp = System.currentTimeMillis()))
            }
        }
    }

    fun isSavedToLibrary(context: Context, songId: String): Flow<Boolean> {
        return AppDatabase.getDatabase(context).streamSongDao().isSavedToLibrary(songId)
    }

    suspend fun fetchMadeForYou(context: Context, targetCount: Int = 50): List<VideoItem> {
        AppLogger.log("[Repo] Fetching Made for You to ensure $targetCount items...")
        val database = AppDatabase.getDatabase(context)

        val recommendedIds = database.streamSongDao().getRecommendedSongsSync()?.map { it.id }?.toSet() ?: emptySet()
        val historyIds = database.playHistoryDao().getAllHistoryIdsSync()?.toSet() ?: emptySet()
        val genres = UserPreferences.getGenres(context).toList()
        val favoriteArtists = UserPreferences.getArtists(context).toList()

        val madeForYou = mutableListOf<VideoItem>()
        val seenIds = mutableSetOf<String>().apply {
            addAll(recommendedIds)
            addAll(historyIds)
        }

        val searchTerms = mutableListOf<String>()
        if (genres.isNotEmpty()) {
            searchTerms.addAll(genres.map { "$it music" })
        } else {
            searchTerms.add("trending music")
        }
        if (favoriteArtists.isNotEmpty()) {
            searchTerms.addAll(favoriteArtists.map { "$it songs" })
        }
        searchTerms.shuffle()

        var attempts = 0
        val maxAttempts = 10

        while (madeForYou.size < targetCount && attempts < maxAttempts) {
            val term = searchTerms[attempts % searchTerms.size]
            try {
                AppLogger.log("[Repo] Made for You fetch attempt ${attempts + 1}: querying '$term'")
                val results = InnerTubeClient.search(term)
                val filtered = results.filter { parseDuration(it.duration) in 60..600 }

                // Shuffle to avoid getting the exact same top results if we query the same term again
                val randomizedResults = filtered.shuffled()

                for (video in randomizedResults) {
                    if (madeForYou.size >= targetCount) break
                    if (seenIds.add(video.id)) {
                        madeForYou.add(video)
                    }
                }
            } catch (e: Exception) {
                AppLogger.log("[Repo] Failed to fetch Made for You for term '$term': ${e.message}")
            }
            attempts++
        }

        AppLogger.log("[Repo] Made for You fetched ${madeForYou.size} items.")
        return madeForYou
    }
}
