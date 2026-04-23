package com.example.musicdownloader.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class ArtistCount(
    val artist: String,
    val playCount: Int
)

@Dao
interface PlayHistoryDao {
    @Query("SELECT * FROM play_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentHistory(limit: Int): Flow<List<PlayHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: PlayHistory)

    @Query("DELETE FROM play_history WHERE timestamp NOT IN (SELECT timestamp FROM play_history ORDER BY timestamp DESC LIMIT 400)")
    suspend fun enforceLimit()

    @Query("DELETE FROM play_history")
    suspend fun clearHistory()

    // DNA Stats Queries
    @Query("SELECT artist, COUNT(*) as playCount FROM play_history GROUP BY artist ORDER BY playCount DESC LIMIT 1")
    fun getTopArtist(): Flow<ArtistCount?>

    @Query("SELECT COUNT(*) FROM play_history")
    fun getTotalPlayCount(): Flow<Int>

    // Get all song IDs in history (for filtering recommendations)
    @Query("SELECT DISTINCT songId FROM play_history")
    fun getAllHistoryIds(): Flow<List<String>>

    @Query("SELECT songId FROM play_history")
    suspend fun getAllHistoryIdsSync(): List<String>


    // Sync query for Smart Shuffle
    @Query("SELECT artist, COUNT(*) as playCount FROM play_history GROUP BY artist ORDER BY playCount DESC LIMIT 1")
    suspend fun getTopArtistSync(): ArtistCount?
}
