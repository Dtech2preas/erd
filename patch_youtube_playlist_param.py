import re

with open("app/src/main/java/com/example/musicdownloader/ui/YouTubePlaylistScreen.kt", "r") as f:
    content = f.read()

content = content.replace("                            onCancelDownload = { viewModel.cancelDownload(video.id) },", "")

with open("app/src/main/java/com/example/musicdownloader/ui/YouTubePlaylistScreen.kt", "w") as f:
    f.write(content)
