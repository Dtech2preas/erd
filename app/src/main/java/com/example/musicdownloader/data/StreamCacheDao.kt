package com.example.musicdownloader.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StreamCacheDao {
    @Query("SELECT * FROM stream_cache WHERE videoId = :videoId")
    suspend fun getStreamCache(videoId: String): StreamCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cache: StreamCache)

    @Query("DELETE FROM stream_cache WHERE expireTime < :currentTime")
    suspend fun clearExpired(currentTime: Long)

    @Query("DELETE FROM stream_cache WHERE videoId = :videoId")
    suspend fun deleteStreamCache(videoId: String)

    @Query("DELETE FROM stream_cache")
    suspend fun clearAll()

    @Query("SELECT videoId FROM stream_cache")
    fun getAllCachedIds(): Flow<List<String>>
}
