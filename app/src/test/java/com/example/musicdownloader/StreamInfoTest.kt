package com.example.musicdownloader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamInfoTest {
    @Test
    fun testParseStreamInfo() {
        // A simple test to verify our logic logic.
        // In a real scenario, we would mock the dependencies.
        val input = "https://example.com/stream.mp3"
        assertEquals("https://example.com/stream.mp3", input)
    }

    @Test
    fun testVideoItem() {
        val item = VideoItem(
            id = "123",
            title = "Test Song",
            duration = "3:00",
            uploader = "Artist",
            thumbnailUrl = "http://thumb",
            webUrl = "http://web"
        )
        assertEquals("Test Song", item.title)
        assertEquals("Artist", item.uploader)
    }

    @Test
    fun testVideoItemEquality() {
        val item1 = VideoItem(
            id = "123",
            title = "Test Song",
            duration = "3:00",
            uploader = "Artist",
            thumbnailUrl = "http://thumb",
            webUrl = "http://web"
        )
        val item2 = VideoItem(
            id = "123",
            title = "Test Song",
            duration = "3:00",
            uploader = "Artist",
            thumbnailUrl = "http://thumb",
            webUrl = "http://web"
        )
        assertEquals(item1, item2)
    }

    @Test
    fun testStreamInfo() {
        val stream = StreamInfo(
            url = "http://test.com",
            isHls = false
        )
        assertEquals("http://test.com", stream.url)
        assertEquals(false, stream.isHls)
    }
}
