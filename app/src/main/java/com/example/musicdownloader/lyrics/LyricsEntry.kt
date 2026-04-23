package com.example.musicdownloader.lyrics

data class LyricsEntry(
    val time: Long,
    val text: String,
) : Comparable<LyricsEntry> {
    override fun compareTo(other: LyricsEntry): Int {
        return time.compareTo(other.time)
    }

    companion object {
        val HEAD_LYRICS_ENTRY = LyricsEntry(0L, "")
    }
}
