import re

with open("app/src/main/java/com/example/musicdownloader/MainActivity.kt", "r") as f:
    content = f.read()

content = content.replace("        is AppScreen.ActiveDownloads -> 1", "        is AppScreen.ActiveDownloads -> 1\n        is AppScreen.YouTubePlaylistDetail -> 1")

with open("app/src/main/java/com/example/musicdownloader/MainActivity.kt", "w") as f:
    f.write(content)
