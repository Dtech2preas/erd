import re

with open("app/src/main/java/com/example/musicdownloader/ui/Navigation.kt", "r") as f:
    nav_content = f.read()

nav_content = nav_content.replace("    data class PlaylistDetail(val id: Int, val name: String) : AppScreen()", "    data class PlaylistDetail(val id: Int, val name: String) : AppScreen()\n    data class YouTubePlaylistDetail(val playlistId: String, val playlistName: String) : AppScreen()")

with open("app/src/main/java/com/example/musicdownloader/ui/Navigation.kt", "w") as f:
    f.write(nav_content)
