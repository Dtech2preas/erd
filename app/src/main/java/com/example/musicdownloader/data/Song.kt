package com.example.musicdownloader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey val id: String, // Video ID
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val filePath: String,
    val duration: String,
    val album: String = "Unknown Album"
)
