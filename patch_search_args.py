import re

with open("app/src/main/java/com/example/musicdownloader/ui/SearchScreen.kt", "r") as f:
    content = f.read()

content = content.replace("onDownload = { viewModel.downloadSong(video) },", "onDownloadClick = { viewModel.downloadSong(video) },")
content = content.replace("onCancelDownload = { viewModel.cancelDownload(video.id) }", "")

with open("app/src/main/java/com/example/musicdownloader/ui/SearchScreen.kt", "w") as f:
    f.write(content)
