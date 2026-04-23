package com.example.musicdownloader.ui

sealed class AppScreen {
    // Root Screens
    object Home : AppScreen()
    data class Search(val query: String? = null) : AppScreen()
    object Identify : AppScreen()
    object Library : AppScreen()
    object Settings : AppScreen()
    object Info : AppScreen()

    // Library Sub-screens
    object Playlists : AppScreen()
    object LikedSongs : AppScreen()
    object Artists : AppScreen()

    // Tools
    object Compression : AppScreen()
    object ActiveDownloads : AppScreen()

    // Details
    data class PlaylistDetail(val id: Int, val name: String) : AppScreen()
    data class YouTubePlaylistDetail(val playlistId: String, val playlistName: String) : AppScreen()
    data class ArtistDetail(val name: String) : AppScreen()
}
