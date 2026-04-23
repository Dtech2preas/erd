import re

with open("app/src/main/java/com/example/musicdownloader/MusicViewModel.kt", "r") as f:
    content = f.read()

# Replace the incorrect dao implementation I added with the correct data types.

old_funcs = """    fun addAllToLibrary(videos: List<VideoItem>, localPlaylistName: String) {
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
    }"""

new_funcs = """    fun addAllToLibrary(videos: List<VideoItem>, localPlaylistName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val db = com.example.musicdownloader.data.AppDatabase.getDatabase(getApplication())
            val playlistDao = db.playlistDao()
            val songDao = db.songDao()

            val allPlaylists = kotlinx.coroutines.flow.first(playlistDao.getAllPlaylists())
            var playlistId = allPlaylists.find { it.name == localPlaylistName }?.id

            if (playlistId == null) {
                playlistId = playlistDao.insertPlaylist(com.example.musicdownloader.data.Playlist(name = localPlaylistName)).toInt()
            }

            for (video in videos) {
                // Add to library if not exist
                val existingSong = songDao.getSongSync(video.id)
                if (existingSong == null) {
                    val newSong = com.example.musicdownloader.data.Song(
                        id = video.id,
                        title = video.title,
                        uploader = video.uploader,
                        duration = video.duration,
                        thumbnailUrl = video.thumbnailUrl
                    )
                    songDao.insertSong(newSong)
                }

                // Add to playlist
                try {
                    playlistDao.addSongToPlaylist(com.example.musicdownloader.data.PlaylistEntry(playlistId, video.id))
                } catch (e: Exception) {
                    // Ignore unique constraint exception
                }
            }
        }
    }"""

content = content.replace(old_funcs, new_funcs)

with open("app/src/main/java/com/example/musicdownloader/MusicViewModel.kt", "w") as f:
    f.write(content)
