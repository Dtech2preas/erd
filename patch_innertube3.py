import re

with open("app/src/main/java/com/example/musicdownloader/InnerTubeClient.kt", "r") as f:
    content = f.read()

content = content.replace('playlists.add(PlaylistItem(id = playlistId, title = title, thumbnailUrl = thumbnailUrl, songCountText = "$videoCount videos"))', 'playlists.add(PlaylistItem(id = playlistId, title = title, thumbnailUrl = thumbnailUrl, songCountText = "${videoCount} videos"))')

with open("app/src/main/java/com/example/musicdownloader/InnerTubeClient.kt", "w") as f:
    f.write(content)
