package com.example.musicdownloader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stream_cache")
data class StreamCache(
    @PrimaryKey val videoId: String,
    val streamUrl: String,
    val expireTime: Long,
    val cachedAt: Long
)
