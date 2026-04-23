package com.example.musicdownloader.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Query("SELECT * FROM playlists")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistEntry(entry: PlaylistEntry)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSongToPlaylist(entry: PlaylistEntry)

    @Query("SELECT s.* FROM songs s INNER JOIN playlist_entries pe ON s.id = pe.songId WHERE pe.playlistId = :playlistId")
    fun getSongsForPlaylist(playlistId: Int): Flow<List<Song>>

    @Query("SELECT * FROM playlist_entries WHERE playlistId = :playlistId")
    fun getPlaylistEntries(playlistId: Int): Flow<List<PlaylistEntry>>

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Int)

    @Query("DELETE FROM playlist_entries WHERE playlistId = :playlistId")
    suspend fun removePlaylistEntries(playlistId: Int)

    @Query("DELETE FROM playlist_entries WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Int, songId: String)

    @Query("UPDATE playlists SET name = :newName WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: Int, newName: String)
}
