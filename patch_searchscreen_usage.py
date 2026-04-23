import re

with open("app/src/main/java/com/example/musicdownloader/MainActivity.kt", "r") as f:
    content = f.read()

content = content.replace("                        onViewDownloads = { navigateTo(AppScreen.ActiveDownloads) }", "                        onViewDownloads = { navigateTo(AppScreen.ActiveDownloads) },\n                        onPlaylistClick = { id, name -> navigateTo(AppScreen.YouTubePlaylistDetail(id, name)) }")

with open("app/src/main/java/com/example/musicdownloader/MainActivity.kt", "w") as f:
    f.write(content)

with open("app/src/main/java/com/example/musicdownloader/ui/Navigation.kt", "r") as f:
    nav_content = f.read()

nav_content = nav_content.replace("    object DownloadDashboard : AppScreen()", "    object DownloadDashboard : AppScreen()\n    data class YouTubePlaylistDetail(val playlistId: String, val playlistName: String) : AppScreen()")

with open("app/src/main/java/com/example/musicdownloader/ui/Navigation.kt", "w") as f:
    f.write(nav_content)
