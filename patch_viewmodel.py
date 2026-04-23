import re

with open("app/src/main/java/com/example/musicdownloader/MusicViewModel.kt", "r") as f:
    content = f.read()

# 1. Update HomeUiState
uistate_re = r'(data class HomeUiState\(\n.*?val results: List<VideoItem> = emptyList\(\),)(.*?)\)'
def uistate_replacer(match):
    return match.group(1) + match.group(2) + ",\n    val playlistResults: List<PlaylistItem> = emptyList(),\n    val playlistVideos: List<VideoItem> = emptyList()\n)"
content = re.sub(uistate_re, uistate_replacer, content, flags=re.DOTALL)

# 2. Add functions
funcs = """
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
            val dao = AppDatabase.getDatabase(getApplication()).playlistDao()
            val existing = dao.getAllPlaylistsSync().find { it.name == localPlaylistName }
            val playlistId = existing?.id ?: java.util.UUID.randomUUID().toString()
            if (existing == null) {
                dao.insertPlaylist(PlaylistEntity(id = playlistId, name = localPlaylistName))
            }

            for (video in videos) {
                // Add to library
                val songDao = AppDatabase.getDatabase(getApplication()).songDao()
                val existingSong = songDao.getSongByIdSync(video.id)
                if (existingSong == null) {
                    val newSong = SongEntity(
                        id = video.id,
                        title = video.title,
                        uploader = video.uploader,
                        duration = video.duration,
                        thumbnailUrl = video.thumbnailUrl,
                        dateAdded = System.currentTimeMillis()
                    )
                    songDao.insertSong(newSong)
                }

                // Check if already in playlist
                val songsInPlaylist = dao.getSongsForPlaylistSync(playlistId)
                if (songsInPlaylist.none { it.songId == video.id }) {
                    dao.addSongToPlaylist(PlaylistSongCrossRef(playlistId, video.id))
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
"""

content = content.replace("    fun search(query: String)", funcs + "\n    fun search(query: String)")

with open("app/src/main/java/com/example/musicdownloader/MusicViewModel.kt", "w") as f:
    f.write(content)
