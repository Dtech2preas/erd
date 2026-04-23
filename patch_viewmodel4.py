import re

with open("app/src/main/java/com/example/musicdownloader/MusicViewModel.kt", "r") as f:
    content = f.read()

content = content.replace("songDao.getSongSync(video.id)", "songDao.getSongById(video.id)")
content = content.replace("songDao.insertSong(newSong)", "songDao.insert(newSong)")

with open("app/src/main/java/com/example/musicdownloader/MusicViewModel.kt", "w") as f:
    f.write(content)
