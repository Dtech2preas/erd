package com.example.musicdownloader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.musicdownloader.data.AppDatabase
import com.example.musicdownloader.data.CompressionManager
import com.example.musicdownloader.data.CompressionQuality
import com.example.musicdownloader.data.PlayHistory
import com.example.musicdownloader.data.Playlist
import com.example.musicdownloader.data.Song
import com.example.musicdownloader.data.StreamSong
import com.example.musicdownloader.utils.DnaAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class SortOption {
    NEWEST_FIRST,
    A_Z,
    Z_A
}

data class DnaStats(
    val topArtist: String = "Unknown",
    val topArtistPlays: Int = 0,
    val totalPlays: Int = 0,
    val favoriteGenre: String = "Various",
    val personalityType: String = "The Newcomer",
    val personalityDescription: String = "Just starting your musical journey."
)

data class MusicUiState(
    val results: List<VideoItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingPlayer: Boolean = false,
    val errorMessage: String? = null,
    val downloadMessage: String? = null,
    val genreFeeds: List<GenreFeed> = emptyList(),
    val madeForYou: List<VideoItem> = emptyList(),
    val playlistResults: List<com.example.musicdownloader.PlaylistItem> = emptyList(),
    val playlistVideos: List<com.example.musicdownloader.VideoItem> = emptyList()
)

data class CurrentSongStatus(
    val mediaItem: MediaItem? = null,
    val isLiked: Boolean = false,
    val isInLibrary: Boolean = false
)

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()

    // Toast Events Channel
    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent = _toastEvent.asSharedFlow()

    // Compression State
    private val _isCompressing = MutableStateFlow(false)
    val isCompressing: StateFlow<Boolean> = _isCompressing.asStateFlow()

    private val _compressionProgress = MutableStateFlow("")
    val compressionProgress: StateFlow<String> = _compressionProgress.asStateFlow()

    // Expose Player State from Manager
    val isPlaying = MusicControllerManager.isPlaying
    val currentMediaItem = MusicControllerManager.currentMediaItem
    val currentPosition = MusicControllerManager.currentPosition
    val duration = MusicControllerManager.duration
    val shuffleModeEnabled = MusicControllerManager.shuffleModeEnabled
    val repeatMode = MusicControllerManager.repeatMode
    val audioSessionId = MusicControllerManager.audioSessionId
    val isSmartShuffleEnabled = MusicControllerManager.isSmartShuffleEnabled

    // Download Progress Flow (Global)
    val downloadProgress = YoutubeClient.downloadProgress

    // Active Downloads List (Derived)
    // Filter out completed ones (100f)
    val activeDownloads: StateFlow<List<DownloadStatus>> = downloadProgress
        .map { it.values.toList().filter { status -> status.progress < 100f } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Initializing Downloads (Waiting for start)
    private val _initializingDownloads = MutableStateFlow<Set<String>>(emptySet())
    val initializingDownloads: StateFlow<Set<String>> = _initializingDownloads.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.NEWEST_FIRST)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _filterDownloadedOnly = MutableStateFlow(false)
    val filterDownloadedOnly: StateFlow<Boolean> = _filterDownloadedOnly.asStateFlow()

    // Library Flow
    private val _unifiedSongs = MusicRepository.getLibrarySongs(application)

    val librarySongs: StateFlow<List<Song>> = combine(_unifiedSongs, _sortOption, _filterDownloadedOnly) { songs, sort, filterDown ->
        val filtered = if (filterDown) songs.filter { !it.filePath.startsWith("stream://") } else songs
        when (sort) {
            SortOption.NEWEST_FIRST -> filtered.reversed()
            SortOption.A_Z -> filtered.sortedBy { it.title.lowercase() }
            SortOption.Z_A -> filtered.sortedByDescending { it.title.lowercase() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recommendedSongs: StateFlow<List<StreamSong>> = MusicRepository.getRecommendedSongs(application)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // History Flow
    val playHistory: StateFlow<List<PlayHistory>> = MusicRepository.getRecentHistory(application)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Favorites Flow
    val likedSongIds: StateFlow<List<String>> = MusicRepository.getLikedSongIds(application)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Cached Stream IDs
    val cachedStreamIds: StateFlow<List<String>> = MusicRepository.getAllCachedIdsFlow(application)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Playlists Flow
    val playlists: StateFlow<List<Playlist>> = MusicRepository.getPlaylists(application)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Unified Current Song Status (Atomic updates)
    val currentSongStatus: StateFlow<CurrentSongStatus> = combine(
        currentMediaItem,
        likedSongIds,
        librarySongs // Uses the unified list (downloads + manual streams)
    ) { mediaItem, likedIds, library ->
        val id = mediaItem?.mediaId
        if (id == null) {
            CurrentSongStatus()
        } else {
            val isLiked = likedIds.contains(id)
            // Check if ID exists in the unified library list
            val isInLibrary = library.any { it.id == id }
            CurrentSongStatus(mediaItem, isLiked, isInLibrary)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CurrentSongStatus())

    // D-TECH DNA Stats Flow
    val dnaStats: StateFlow<DnaStats> = combine(
        MusicRepository.getTopArtist(application),
        MusicRepository.getTotalPlayCount(application)
    ) { topArtist, totalPlays ->
        val genres = UserPreferences.getGenres(application)
        val favGenre = genres.firstOrNull() ?: "Various"

        val (type, desc) = DnaAnalyzer.calculatePersonality(topArtist, totalPlays, genres.size)

        DnaStats(
            topArtist = topArtist?.artist ?: "None Yet",
            topArtistPlays = topArtist?.playCount ?: 0,
            totalPlays = totalPlays,
            favoriteGenre = favGenre,
            personalityType = type,
            personalityDescription = desc
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DnaStats())

    init {
        // Initialize the controller connection
        MusicControllerManager.initialize(application)

        // Sync files on startup
        viewModelScope.launch {
            MusicRepository.syncFilesWithDatabase(application)
        }

        // Initialize Recommendations
        viewModelScope.launch {
            MusicRepository.refreshRecommendations(application)
        }

        // Load Genre Feeds
        loadGenreFeeds()

        // Polling loop for position updates
        viewModelScope.launch {
            while (true) { // Use true with delay
                if (isPlaying.value) {
                    MusicControllerManager.updatePosition()
                }
                delay(1000)
            }
        }

        // Monitor download progress to clear initializing state
        viewModelScope.launch {
            downloadProgress.collect { progressMap ->
                val currentInitializing = _initializingDownloads.value
                if (currentInitializing.isNotEmpty()) {
                    // Remove IDs that have started downloading (progress > 0)
                    val newInitializing = currentInitializing.filter { id ->
                        val status = progressMap[id]
                        status == null || status.progress <= 0f
                    }.toSet()

                    if (newInitializing.size != currentInitializing.size) {
                        _initializingDownloads.value = newInitializing
                    }
                }
            }
        }
    }

    fun loadGenreFeeds() {
        val genres = UserPreferences.getGenres(getApplication())
        if (genres.isEmpty()) return

        // Show loading via state if desired, but we want Skeleton behavior which checks empty/loading.
        _uiState.value = _uiState.value.copy(isLoading = true)

        viewModelScope.launch {
            // Launch both fetches concurrently
            val feedsDeferred = async { MusicRepository.fetchGenreFeeds(getApplication(), genres) }
            val madeForYouDeferred = async { MusicRepository.fetchMadeForYou(getApplication()) }

            val feeds = feedsDeferred.await()
            val madeForYou = madeForYouDeferred.await()

            _uiState.value = _uiState.value.copy(
                genreFeeds = feeds,
                madeForYou = madeForYou,
                isLoading = false
            )
        }
    }

    fun refreshRecommendations() {
        viewModelScope.launch {
            MusicRepository.refreshRecommendations(getApplication())
        }
    }


    fun searchPlaylists(query: String) {
        if (query.isBlank()) return
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val result = MusicRepository.searchPlaylists(getApplication(), query)
            result.onSuccess { playlists ->
                _uiState.value = _uiState.value.copy(
                    playlistResults = playlists,
                    isLoading = false
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Unknown search error"
                )
            }
        }
    }

    fun loadPlaylistVideos(playlistId: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, playlistVideos = emptyList())
        viewModelScope.launch {
            val result = MusicRepository.getPlaylistVideos(getApplication(), playlistId)
            result.onSuccess { videos ->
                _uiState.value = _uiState.value.copy(
                    playlistVideos = videos,
                    isLoading = false
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to load playlist videos"
                )
            }
        }
    }

    fun addAllToLibrary(videos: List<VideoItem>, localPlaylistName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val db = com.example.musicdownloader.data.AppDatabase.getDatabase(getApplication())
            val playlistDao = db.playlistDao()
            val songDao = db.songDao()

            val allPlaylists = playlistDao.getAllPlaylists().first()
            var playlistId = allPlaylists.find { it.name == localPlaylistName }?.id

            if (playlistId == null) {
                playlistId = playlistDao.insertPlaylist(com.example.musicdownloader.data.Playlist(name = localPlaylistName)).toInt()
            }

            for (video in videos) {
                // Add to library if not exist
                val existingSong = songDao.getSongById(video.id)
                if (existingSong == null) {
                    val newSong = com.example.musicdownloader.data.Song(
                        id = video.id,
                        title = video.title,
                        artist = video.uploader,
                        duration = video.duration,
                        thumbnailUrl = video.thumbnailUrl,
                        filePath = ""
                    )
                    songDao.insert(newSong)
                }

                // Add to playlist
                try {
                    playlistDao.addSongToPlaylist(com.example.musicdownloader.data.PlaylistEntry(playlistId, video.id))
                } catch (e: Exception) {
                    // Ignore unique constraint exception
                }
            }
        }
    }

    fun downloadAll(videos: List<VideoItem>, localPlaylistName: String) {
        addAllToLibrary(videos, localPlaylistName)
        for (video in videos) {
            downloadSong(video)
        }
    }

    fun search(query: String) {
        if (query.isBlank()) return

        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            val result = MusicRepository.searchVideos(getApplication(), query)
            result.onSuccess { videos ->
                _uiState.value = _uiState.value.copy(
                    results = videos,
                    isLoading = false
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Unknown search error"
                )
            }
        }
    }

    fun playStream(video: VideoItem) {
        AppLogger.log("[ViewModel] playStream called for ${video.id}")
        AppLogger.log("[ViewModel] Video Details: Title='${video.title}', Uploader='${video.uploader}', Duration=${video.duration}")
        AppLogger.log("[ViewModel] WebURL=${video.webUrl}, Thumbnail=${video.thumbnailUrl}")

        // Track history immediately
        viewModelScope.launch(Dispatchers.IO) {
            try {
                MusicRepository.addToHistory(getApplication(), video)
                MusicRepository.autoAddStreamSong(getApplication(), video)
                AppLogger.log("[ViewModel] Added to history: ${video.title}")
            } catch (e: Exception) {
                AppLogger.log("[ViewModel] Error adding to history: ${e.message}")
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            val file = File(getApplication<Application>().filesDir, "music_downloads/${video.id}")
            val outputDir = File(getApplication<Application>().filesDir, "music_downloads")

            AppLogger.log("[ViewModel] Checking local file at default path: ${file.absolutePath}")
            val existingFiles = outputDir.listFiles { _, name -> name.startsWith(video.id) }
            val targetFile = existingFiles?.firstOrNull() ?: file

            if (targetFile.exists()) {
                 AppLogger.log("[ViewModel] Local file FOUND at: ${targetFile.absolutePath}")
                 // Play local file immediately
                 withContext(Dispatchers.Main) {
                     playSong(video.id, video.title, video.uploader, video.thumbnailUrl)
                     _toastEvent.emit("Playing downloaded file...")
                 }
                 return@launch
            } else {
                 AppLogger.log("[ViewModel] Local file NOT FOUND. Proceeding to stream.")
            }

            // Not found locally, start streaming
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(isLoadingPlayer = true)
                _toastEvent.emit("Fetching stream for ${video.title}...")
            }

            try {
                AppLogger.log("[ViewModel] Calling MusicRepository.getStreamUrlWithCache...")
                val streamInfo = MusicRepository.getStreamUrlWithCache(getApplication(), video.id, video.webUrl)
                AppLogger.log("[ViewModel] Stream Info received. URL Length: ${streamInfo.url.length}, isHls: ${streamInfo.isHls}")

                if (streamInfo.url.isNotBlank()) {
                    AppLogger.log("[ViewModel] Building MediaMetadata...")
                    val mediaMetadata = MediaMetadata.Builder()
                        .setTitle(video.title)
                        .setArtist(video.uploader)
                        .setArtworkUri(android.net.Uri.parse(video.thumbnailUrl))
                        .build()

                    AppLogger.log("[ViewModel] Building MediaItem with URI: ${streamInfo.url}")
                    val mediaItem = MediaItem.Builder()
                        .setUri(streamInfo.url)
                        .setMediaId(video.id)
                        .setMediaMetadata(mediaMetadata)
                        .build()

                    withContext(Dispatchers.Main) {
                        AppLogger.log("[ViewModel] Dispatching playMedia to MusicControllerManager...")
                        MusicControllerManager.playMedia(mediaItem)
                    }
                } else {
                    AppLogger.log("[ViewModel] ERROR: Stream URL is blank!")
                }
            } catch (e: Exception) {
                AppLogger.log("[ViewModel] Streaming failed with exception: ${e.message}")
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                     _toastEvent.emit("Streaming failed: ${e.message}")
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isLoadingPlayer = false)
                }
            }
        }
    }

    fun downloadSong(video: VideoItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(getApplication<Application>().filesDir, "music_downloads/${video.id}")
            val outputDir = File(getApplication<Application>().filesDir, "music_downloads")
            val existingFiles = outputDir.listFiles { _, name -> name.startsWith(video.id) }
            val targetFile = existingFiles?.firstOrNull() ?: file

            if (targetFile.exists()) {
                 withContext(Dispatchers.Main) {
                     _toastEvent.emit("File already exists")
                 }
                 return@launch
            }

            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(downloadMessage = "Downloading ${video.title}...")
                _initializingDownloads.value += video.id
                YoutubeClient.initializeDownloadStatus(video)
            }

            try {
                _toastEvent.emit("Download started for ${video.title}")
                val result = MusicRepository.downloadSong(getApplication(), video)

                result.onSuccess { msg ->
                    withContext(Dispatchers.Main) {
                         _uiState.value = _uiState.value.copy(downloadMessage = msg)
                    }
                    if (msg == "File already exists") {
                        withContext(Dispatchers.Main) {
                             _initializingDownloads.value -= video.id
                        }
                    }
                }.onFailure { e ->
                    withContext(Dispatchers.Main) {
                        _initializingDownloads.value -= video.id
                        _toastEvent.emit("Download failed: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _initializingDownloads.value -= video.id
                    _toastEvent.emit("Error starting download: ${e.message}")
                }
                AppLogger.log("[ViewModel] Download Error: ${e.message}")
            }
        }
    }

    @Deprecated("Use playStream or downloadSong instead")
    fun downloadAndPlay(video: VideoItem) {
        AppLogger.log("[ViewModel] downloadAndPlay is deprecated. Delegating to playStream.")
        playStream(video)
    }

    fun pauseDownload(videoId: String) {
        viewModelScope.launch {
            MusicRepository.pauseDownload(getApplication(), videoId)
            _toastEvent.emit("Download paused")
        }
    }

    fun resumeDownload(videoId: String) {
        // Find the original VideoItem from status
        val status = downloadProgress.value[videoId]
        if (status?.videoItem != null) {
            viewModelScope.launch {
                // Re-enqueue
                // We should probably check if it's already running? status.isPaused should be true.
                _toastEvent.emit("Resuming download...")
                val result = withContext(Dispatchers.IO) {
                    MusicRepository.downloadSong(getApplication(), status.videoItem)
                }
                result.onSuccess {
                    // Update status to not paused (YoutubeClient logic might need to be refreshed or wait for worker)
                    // The worker will start and call updateProgress which overwrites status, effectively unpausing it.
                }.onFailure {
                    _toastEvent.emit("Failed to resume")
                }
            }
        } else {
            // Should not happen if we initialized correctly
            viewModelScope.launch {
                _toastEvent.emit("Cannot resume: Metadata lost")
            }
        }
    }

    fun deleteDownload(videoId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                MusicRepository.deleteDownload(getApplication(), videoId)
            }
            _initializingDownloads.value -= videoId
            _toastEvent.emit("Download cancelled and deleted")
        }
    }

    fun playSong(id: String, title: String, artist: String, thumbnailUrl: String, contextQueue: List<Song>? = null) {
        if (id.isBlank()) {
            AppLogger.log("[ViewModel] playSong called with empty ID! Aborting.")
            viewModelScope.launch {
                _toastEvent.emit("Cannot play: Song ID is invalid.")
            }
            return
        }
        AppLogger.log("[ViewModel] playSong called: id=$id, title=$title")

        // Track history
        viewModelScope.launch(Dispatchers.IO) {
            try {
                MusicRepository.addToHistory(getApplication(),
                    VideoItem(id, title, "", artist, thumbnailUrl, "")
                )
            } catch (e: Exception) {
                 e.printStackTrace()
            }
        }

        // Use the contextQueue if provided, otherwise default to Library (All Songs)
        val queueToUse = contextQueue ?: librarySongs.value
        val index = queueToUse.indexOfFirst { it.id == id }

        AppLogger.log("[ViewModel] Playing song $title from list (${queueToUse.size} items)")
        AppLogger.log("[ViewModel] Found index in queue: $index")

        if (index != -1) {
            AppLogger.log("[ViewModel] Delegating to MusicControllerManager.playPlaylist")
            MusicControllerManager.playPlaylist(queueToUse, index)
        } else {
            // Fallback for non-library play (e.g. search result not in library yet)
             AppLogger.log("[ViewModel] Song not in current queue. Attempting direct file playback.")
             val file = File(getApplication<Application>().filesDir, "music_downloads/$id")
             val outputDir = File(getApplication<Application>().filesDir, "music_downloads")
             val existingFiles = outputDir.listFiles { _, name -> name.startsWith(id) }
             val targetFile = existingFiles?.firstOrNull() ?: file

             AppLogger.log("[ViewModel] Direct file playback: Path=${targetFile.absolutePath}, Exists=${targetFile.exists()}")

             val mediaMetadata = MediaMetadata.Builder()
                 .setTitle(title)
                 .setArtist(artist)
                 .setArtworkUri(android.net.Uri.parse(thumbnailUrl))
                 .build()

             val mediaItem = MediaItem.Builder()
                 .setUri(android.net.Uri.fromFile(targetFile))
                 .setMediaId(id)
                 .setMediaMetadata(mediaMetadata)
                 .build()

             AppLogger.log("[ViewModel] Delegating to MusicControllerManager.playMedia")
             MusicControllerManager.playMedia(mediaItem)
        }
    }

    @Deprecated("Use playSong instead")
    fun playLocalSong(id: String, title: String, artist: String, thumbnailUrl: String) {
        playSong(id, title, artist, thumbnailUrl, null)
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
    }

    fun toggleFilterDownloadedOnly() {
        _filterDownloadedOnly.value = !_filterDownloadedOnly.value
    }

    private fun getCurrentStreamUrl(videoId: String): String? {
        val item = currentMediaItem.value
        if (item?.mediaId == videoId) {
            val uri = item.localConfiguration?.uri
            if (uri != null && (uri.scheme == "http" || uri.scheme == "https")) {
                return uri.toString()
            }
        }
        return null
    }

    fun addToLibrary(video: VideoItem) {
        viewModelScope.launch {
            val streamUrl = getCurrentStreamUrl(video.id)
            MusicRepository.addToLibrary(getApplication(), video, streamUrl)
            _toastEvent.emit("Added to Library")
        }
    }

    fun toggleLibraryStatus(video: VideoItem, isCurrentlySaved: Boolean) {
        viewModelScope.launch {
            if (isCurrentlySaved) {
                MusicRepository.removeFromLibrary(getApplication(), video.id)
                _toastEvent.emit("Removed from Library")
            } else {
                val streamUrl = getCurrentStreamUrl(video.id)
                MusicRepository.addToLibrary(getApplication(), video, streamUrl)
                _toastEvent.emit("Added to Library")
            }
        }
    }

    fun isSavedToLibrary(id: String): kotlinx.coroutines.flow.Flow<Boolean> {
        return MusicRepository.isSavedToLibrary(getApplication(), id)
    }

    suspend fun addToQueue(song: Song): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Determine if it is a local file or stream
                val isStream = song.filePath.startsWith("stream://")
                val uri: android.net.Uri

                if (isStream) {
                    val id = song.filePath.removePrefix("stream://")
                    uri = android.net.Uri.parse("dtech://stream/$id")
                } else {
                    val file = File(song.filePath)
                    if (file.exists()) {
                        uri = android.net.Uri.fromFile(file)
                    } else {
                        // Smart discovery fallback
                        val outputDir = File(getApplication<Application>().filesDir, "music_downloads")
                        val found = outputDir.listFiles { _, name -> name.startsWith(song.id) }?.firstOrNull()
                        if (found != null) {
                            uri = android.net.Uri.fromFile(found)
                        } else {
                            // Fallback to stream if file completely missing
                            uri = android.net.Uri.parse("dtech://stream/${song.id}")
                        }
                    }
                }

                val mediaItem = MediaItem.Builder()
                    .setUri(uri)
                    .setMediaId(song.id)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setArtworkUri(android.net.Uri.parse(song.thumbnailUrl))
                            .build()
                    )
                    .build()

                MusicControllerManager.addMediaItemToQueue(mediaItem)
                true
            } catch (e: Exception) {
                AppLogger.log("[ViewModel] Error adding to queue: ${e.message}")
                // _toastEvent.emit("Failed to add to queue") // Let caller handle
                false
            }
        }
    }

    fun addToQueue(video: VideoItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Check if downloaded
                val outputDir = File(getApplication<Application>().filesDir, "music_downloads")
                val found = outputDir.listFiles { _, name -> name.startsWith(video.id) }?.firstOrNull()

                val uri = if (found != null) {
                    android.net.Uri.fromFile(found)
                } else {
                    android.net.Uri.parse("dtech://stream/${video.id}")
                }

                val mediaItem = MediaItem.Builder()
                    .setUri(uri)
                    .setMediaId(video.id)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(video.title)
                            .setArtist(video.uploader)
                            .setArtworkUri(android.net.Uri.parse(video.thumbnailUrl))
                            .build()
                    )
                    .build()

                MusicControllerManager.addMediaItemToQueue(mediaItem)
                _toastEvent.emit("Added to queue")
            } catch (e: Exception) {
                AppLogger.log("[ViewModel] Error adding video to queue: ${e.message}")
                _toastEvent.emit("Failed to add to queue")
            }
        }
    }

    fun deleteSong(song: Song) {
        viewModelScope.launch {
            // 1. Remove from DB
            AppDatabase.getDatabase(getApplication()).songDao().deleteById(song.id)

            // 2. Move file to "trash" (rename to .deleted)
            withContext(Dispatchers.IO) {
                val file = File(song.filePath)
                if (file.exists()) {
                    file.renameTo(File(file.absolutePath + ".deleted"))
                }
            }
        }
    }

    fun restoreSong(song: Song) {
        viewModelScope.launch {
            // 1. Restore file from "trash"
            withContext(Dispatchers.IO) {
                val deletedFile = File(song.filePath + ".deleted")
                if (deletedFile.exists()) {
                    deletedFile.renameTo(File(song.filePath))
                }
            }

            // 2. Re-insert into DB
            AppDatabase.getDatabase(getApplication()).songDao().insert(song)
        }
    }

    fun finalizeDelete(song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            val deletedFile = File(song.filePath + ".deleted")
            if (deletedFile.exists()) {
                AppLogger.log("[ViewModel] Finalizing delete for ${song.title}")
                deletedFile.delete()
            }
        }
    }

    fun togglePlayPause() {
        if (isPlaying.value) {
            MusicControllerManager.pause()
        } else {
            MusicControllerManager.play()
        }
    }

    fun toggleShuffle() {
        MusicControllerManager.toggleShuffleMode()
    }

    fun toggleSmartShuffle() {
        MusicControllerManager.toggleSmartShuffle()
    }

    fun toggleRepeatMode() {
        MusicControllerManager.toggleRepeatMode()
    }

    fun skipToPrevious() {
        MusicControllerManager.skipToPrevious()
    }

    fun skipToNext() {
        MusicControllerManager.skipToNext()
    }

    fun seekTo(position: Long) {
        MusicControllerManager.seekTo(position)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun clearDownloadMessage() {
        _uiState.value = _uiState.value.copy(downloadMessage = null)
    }

    // Deprecated: used old logic, but now mapped to deleteDownload for consistency if called
    fun cancelDownload(videoId: String) {
        deleteDownload(videoId)
    }

    // Genre Management
    fun addGenre(genre: String) {
        UserPreferences.addGenre(getApplication(), genre)
        loadGenreFeeds()
    }

    fun removeGenre(genre: String) {
        UserPreferences.removeGenre(getApplication(), genre)
        loadGenreFeeds()
    }

    // --- Favorites Logic ---
    fun toggleLike(video: VideoItem) {
        val songId = video.id
        val isLiked = likedSongIds.value.contains(songId)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                MusicRepository.setLikeStatus(getApplication(), songId, !isLiked)
                if (!isLiked) {
                    // Auto-add to library when liking
                    // Pass stream URL if available
                    // Note: Cannot call getCurrentStreamUrl from IO dispatcher if it accesses StateFlow value?
                    // Actually StateFlow value is thread-safe.
                    val streamUrl = getCurrentStreamUrl(video.id)
                    MusicRepository.addToLibrary(getApplication(), video, streamUrl)
                }
                val msg = if (isLiked) "Removed from Liked Songs" else "Added to Liked Songs"
                _toastEvent.emit(msg)
            } catch (e: Exception) {
                AppLogger.log("[ViewModel] Error toggling like: ${e.message}")
                _toastEvent.emit("Failed to update favorites")
            }
        }
    }

    // --- Playlist Logic ---
    fun createPlaylist(name: String) {
        viewModelScope.launch {
            MusicRepository.createPlaylist(getApplication(), name)
        }
    }

    fun renamePlaylist(playlistId: Int, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                AppDatabase.getDatabase(getApplication()).playlistDao().renamePlaylist(playlistId, newName)
                _toastEvent.emit("Playlist renamed")
            } catch (e: Exception) {
                _toastEvent.emit("Failed to rename playlist")
            }
        }
    }

    fun deletePlaylist(playlistId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(getApplication())
                db.playlistDao().deletePlaylist(playlistId)
                db.playlistDao().removePlaylistEntries(playlistId)
                _toastEvent.emit("Playlist deleted")
            } catch (e: Exception) {
                _toastEvent.emit("Failed to delete playlist")
            }
        }
    }

    fun removeSongFromPlaylist(playlistId: Int, songId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                AppDatabase.getDatabase(getApplication()).playlistDao().removeSongFromPlaylist(playlistId, songId)
                _toastEvent.emit("Removed from playlist")
            } catch (e: Exception) {
                _toastEvent.emit("Failed to remove song")
            }
        }
    }

    fun addSongToPlaylist(playlist: Playlist, songs: List<Song>) {
        viewModelScope.launch {
             // In the future, batch add. For now, loop.
             songs.forEach { song ->
                  MusicRepository.addSongToPlaylist(getApplication(), playlist.id, song.id)
             }
        }
    }

    fun addSongToPlaylist(playlistId: Int, video: VideoItem) {
        viewModelScope.launch {
            MusicRepository.addSongToPlaylist(getApplication(), playlistId, video.id)
            // Auto-add to library when adding to playlist
            val streamUrl = getCurrentStreamUrl(video.id)
            MusicRepository.addToLibrary(getApplication(), video, streamUrl)
        }
    }

    // Keep old signature for compatibility if needed, but prefer VideoItem
    fun addSongToPlaylist(playlistId: Int, songId: String) {
        viewModelScope.launch {
            MusicRepository.addSongToPlaylist(getApplication(), playlistId, songId)
        }
    }

    fun getSongsForPlaylist(playlistId: Int): kotlinx.coroutines.flow.Flow<List<Song>> {
        return MusicRepository.getPlaylistSongs(getApplication(), playlistId)
    }

    fun rescanLibrary() {
        viewModelScope.launch {
            _toastEvent.emit("Starting library scan...")
            try {
                MusicRepository.rescanLibrary(getApplication())
                _toastEvent.emit("Scan complete. Metadata updated.")
            } catch (e: Exception) {
                AppLogger.log("[ViewModel] Scan failed: ${e.message}")
                _toastEvent.emit("Scan failed: ${e.message}")
            }
        }
    }

    // New Features
    fun importLocalSongs() {
        viewModelScope.launch {
            _toastEvent.emit("Scanning local files...")
            try {
                val count = MusicRepository.importLocalSongs(getApplication())
                _toastEvent.emit("Imported $count songs.")
            } catch (e: Exception) {
                _toastEvent.emit("Import failed: ${e.message}")
            }
        }
    }

    fun updateSongMetadata(song: Song, newTitle: String, newArtist: String, newAlbum: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                AppDatabase.getDatabase(getApplication()).songDao().updateMetadata(
                    id = song.id,
                    title = newTitle,
                    artist = newArtist,
                    album = newAlbum
                )
                _toastEvent.emit("Metadata updated")
            } catch (e: Exception) {
                _toastEvent.emit("Update failed: ${e.message}")
            }
        }
    }

    fun launchEqualizer() {
        MusicControllerManager.launchEqualizer(getApplication())
    }

    // --- Sleep Timer ---
    private var sleepTimerJob: kotlinx.coroutines.Job? = null
    private val _sleepTimerRemaining = MutableStateFlow<Long?>(null)
    val sleepTimerRemaining: StateFlow<Long?> = _sleepTimerRemaining.asStateFlow()

    fun startSleepTimer(minutes: Int) {
        cancelSleepTimer()
        if (minutes <= 0) return

        val durationMs = minutes * 60 * 1000L
        val endTime = System.currentTimeMillis() + durationMs

        viewModelScope.launch {
             _toastEvent.emit("Sleep timer set for $minutes minutes")
        }

        sleepTimerJob = viewModelScope.launch {
            while (System.currentTimeMillis() < endTime) {
                _sleepTimerRemaining.value = endTime - System.currentTimeMillis()
                delay(1000)
            }
            _sleepTimerRemaining.value = null
            MusicControllerManager.pause()
            _toastEvent.emit("Sleep timer finished")
        }
    }

    fun cancelSleepTimer() {
        if (sleepTimerJob != null) {
            sleepTimerJob?.cancel()
            sleepTimerJob = null
            _sleepTimerRemaining.value = null
            viewModelScope.launch {
                 _toastEvent.emit("Sleep timer cancelled")
            }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        MusicControllerManager.setPlaybackSpeed(speed)
    }

    fun compressSongs(songs: List<Song>, quality: CompressionQuality) {
        viewModelScope.launch(Dispatchers.IO) {
            _isCompressing.value = true
            var successCount = 0
            var failCount = 0
            val total = songs.size

            songs.forEachIndexed { index, song ->
                _compressionProgress.value = "Compressing ${index + 1} of $total...\n${song.title}"

                // Optional: Check if already compressed? No easy way unless we store bitrate metadata.
                // We assume user knows what they are doing.

                val resultFile = CompressionManager.compressSong(getApplication(), song, quality)
                if (resultFile != null) {
                    try {
                        MusicRepository.replaceSongFile(getApplication(), song, resultFile)
                        successCount++
                    } catch (e: Exception) {
                        AppLogger.log("[ViewModel] Replace failed: ${e.message}")
                        failCount++
                    }
                } else {
                    failCount++
                }
            }

            _isCompressing.value = false
            _compressionProgress.value = ""
            _toastEvent.emit("Compression complete. Success: $successCount, Failed: $failCount")
        }
    }
}
