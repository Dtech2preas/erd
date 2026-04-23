import re

with open("app/src/main/java/com/example/musicdownloader/MainActivity.kt", "r") as f:
    content = f.read()

# Implement YouTubePlaylistDetail in MainActivity
youtube_playlist_branch = """
                    is AppScreen.YouTubePlaylistDetail -> com.example.musicdownloader.ui.YouTubePlaylistScreen(
                        playlistId = targetScreen.playlistId,
                        playlistName = targetScreen.playlistName,
                        viewModel = viewModel,
                        contentPadding = PaddingValues(bottom = 80.dp),
                        onBack = { popBackStack() }
                    )
"""
content = content.replace("                    is AppScreen.YouTubePlaylistDetail -> {\n                        // Will implement in next step\n                    }", youtube_playlist_branch)

with open("app/src/main/java/com/example/musicdownloader/MainActivity.kt", "w") as f:
    f.write(content)
