package com.example.musicdownloader.utils

import com.example.musicdownloader.data.ArtistCount

object DnaAnalyzer {
    fun calculatePersonality(
        topArtist: ArtistCount?,
        totalPlays: Int,
        genreCount: Int
    ): Pair<String, String> {
        val type = when {
            totalPlays < 50 -> "The Newcomer"
            totalPlays > 500 -> "The Audiophile"
            (topArtist?.playCount ?: 0) > (totalPlays / 2) && totalPlays > 20 -> "The Super Fan"
            genreCount > 4 -> "The Explorer"
            else -> "The Vibe Setter"
        }

        val desc = when(type) {
            "The Newcomer" -> "Just starting your musical journey."
            "The Audiophile" -> "Music is not just a hobby, it's a lifestyle."
            "The Super Fan" -> "Loyalty is your middle name."
            "The Explorer" -> "You leave no genre unturned."
            "The Vibe Setter" -> "You always know the right track for the moment."
            else -> "Music connects us all."
        }
        return type to desc
    }
}
