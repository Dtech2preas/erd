package com.example.musicdownloader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stream_songs")
data class StreamSong(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: String,
    val thumbnailUrl: String,
    val isManual: Boolean = false, // True = Added to Library, False = Auto/Recommended
    val timestamp: Long = System.currentTimeMillis() // When it was added/cached
)
