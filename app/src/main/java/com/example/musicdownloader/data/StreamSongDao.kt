package com.example.musicdownloader.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StreamSongDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(song: StreamSong)

    @Query("DELETE FROM stream_songs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM stream_songs WHERE id = :id")
    suspend fun getStreamSongById(id: String): StreamSong?

    // Library Songs (Manually added)
    @Query("SELECT * FROM stream_songs WHERE isManual = 1 ORDER BY timestamp DESC")
    fun getLibrarySongs(): Flow<List<StreamSong>>

    // Recommended / Auto-Cached Songs (Not manually added)
    @Query("SELECT * FROM stream_songs WHERE isManual = 0 ORDER BY timestamp DESC")
    fun getRecommendedSongs(): Flow<List<StreamSong>>

    // All Stream Songs (Internal use)
    @Query("SELECT * FROM stream_songs")
    fun getAllStreamSongs(): Flow<List<StreamSong>>

    // Sync methods for synchronous access
    @Query("SELECT * FROM stream_songs WHERE isManual = 1 ORDER BY timestamp DESC")
    suspend fun getLibrarySongsSync(): List<StreamSong>

    @Query("SELECT * FROM stream_songs WHERE isManual = 0 ORDER BY timestamp DESC")
    suspend fun getRecommendedSongsSync(): List<StreamSong>


    // Pruning: Delete auto songs older than X timestamp
    @Query("DELETE FROM stream_songs WHERE isManual = 0 AND timestamp < :timestamp")
    suspend fun deleteExpiredAutoSongs(timestamp: Long)

    // Clear all recommended songs (isManual=0)
    @Query("DELETE FROM stream_songs WHERE isManual = 0")
    suspend fun clearRecommended()

    // Check if song exists in library (manual)
    @Query("SELECT EXISTS(SELECT 1 FROM stream_songs WHERE id = :id AND isManual = 1)")
    fun isSavedToLibrary(id: String): Flow<Boolean>
}
