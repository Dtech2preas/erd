package com.example.musicdownloader.utils

import com.example.musicdownloader.data.ArtistCount
import org.junit.Assert.assertEquals
import org.junit.Test

class DnaAnalyzerTest {

    @Test
    fun `test Newcomer logic`() {
        val (type, _) = DnaAnalyzer.calculatePersonality(null, 10, 0)
        assertEquals("The Newcomer", type)
    }

    @Test
    fun `test Audiophile logic`() {
        val (type, _) = DnaAnalyzer.calculatePersonality(null, 600, 0)
        assertEquals("The Audiophile", type)
    }

    @Test
    fun `test Super Fan logic`() {
        val topArtist = ArtistCount("Drake", 60)
        // Total plays 100. Artist 60. > 50%. Total > 20.
        val (type, _) = DnaAnalyzer.calculatePersonality(topArtist, 100, 0)
        assertEquals("The Super Fan", type)
    }

    @Test
    fun `test Explorer logic`() {
        // Total 200 (not audiophile), not super fan (null artist), but genres > 4
        val (type, _) = DnaAnalyzer.calculatePersonality(null, 200, 5)
        assertEquals("The Explorer", type)
    }

    @Test
    fun `test Vibe Setter logic`() {
        // Default case
        val (type, _) = DnaAnalyzer.calculatePersonality(null, 100, 2)
        assertEquals("The Vibe Setter", type)
    }
}
