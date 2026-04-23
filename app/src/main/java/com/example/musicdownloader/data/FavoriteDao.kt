package com.example.musicdownloader.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteSong)

    @Query("DELETE FROM favorite_songs WHERE songId = :songId")
    suspend fun deleteById(songId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_songs WHERE songId = :songId)")
    fun isLiked(songId: String): Flow<Boolean>

    @Query("SELECT songId FROM favorite_songs")
    fun getAllLikedIds(): Flow<List<String>>

    @Query("SELECT songId FROM favorite_songs")
    suspend fun getAllLikedIdsSync(): List<String>
}
