import re

with open("app/src/main/java/com/example/musicdownloader/MusicViewModel.kt", "r") as f:
    content = f.read()

# Fix Song instantiation
song_inst_old = """                    val newSong = com.example.musicdownloader.data.Song(
                        id = video.id,
                        title = video.title,
                        uploader = video.uploader,
                        duration = video.duration,
                        thumbnailUrl = video.thumbnailUrl
                    )"""

song_inst_new = """                    val newSong = com.example.musicdownloader.data.Song(
                        id = video.id,
                        title = video.title,
                        artist = video.uploader,
                        duration = video.duration,
                        thumbnailUrl = video.thumbnailUrl,
                        filePath = ""
                    )"""

content = content.replace(song_inst_old, song_inst_new)

with open("app/src/main/java/com/example/musicdownloader/MusicViewModel.kt", "w") as f:
    f.write(content)
