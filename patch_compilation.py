import re

# 1. InnerTubeClient
with open("app/src/main/java/com/example/musicdownloader/InnerTubeClient.kt", "r") as f:
    content = f.read()

content = content.replace('playlists.add(PlaylistItem(id = playlistId, title = title, thumbnailUrl = thumbnailUrl, songCountText = videoCountText))', 'playlists.add(PlaylistItem(id = playlistId, title = title, thumbnailUrl = thumbnailUrl, songCountText = videoCountText.toString()))')
content = content.replace('playlists.add(PlaylistItem(id = playlistId, title = title, thumbnailUrl = thumbnailUrl, songCountText = "$videoCount videos"))', 'playlists.add(PlaylistItem(id = playlistId, title = title, thumbnailUrl = thumbnailUrl, songCountText = "$videoCount videos"))')

with open("app/src/main/java/com/example/musicdownloader/InnerTubeClient.kt", "w") as f:
    f.write(content)

# 2. MusicViewModel
with open("app/src/main/java/com/example/musicdownloader/MusicViewModel.kt", "r") as f:
    content = f.read()

content = content.replace('val playlistResults: List<PlaylistItem> = emptyList(),', 'val playlistResults: List<com.example.musicdownloader.PlaylistItem> = emptyList(),')

with open("app/src/main/java/com/example/musicdownloader/MusicViewModel.kt", "w") as f:
    f.write(content)
