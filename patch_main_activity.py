import re

with open("app/src/main/java/com/example/musicdownloader/MainActivity.kt", "r") as f:
    content = f.read()

# Add empty branch for YouTubePlaylistDetail
empty_branch = """
                    is AppScreen.YouTubePlaylistDetail -> {
                        // Will implement in next step
                    }
"""
content = content.replace("                    is AppScreen.ArtistDetail -> ArtistDetailScreen(", empty_branch + "                    is AppScreen.ArtistDetail -> ArtistDetailScreen(")
content = content.replace("                    is AppScreen.ArtistDetail -> false", "                    is AppScreen.YouTubePlaylistDetail -> false\n                    is AppScreen.ArtistDetail -> false")

with open("app/src/main/java/com/example/musicdownloader/MainActivity.kt", "w") as f:
    f.write(content)
