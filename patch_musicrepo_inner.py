import re

with open("app/src/main/java/com/example/musicdownloader/MusicRepository.kt", "r") as f:
    content = f.read()

content = content.replace(
    "val streamInfo = YoutubeClient.getStreamUrl(context, webUrl)",
    "val streamInfo = InnerTubeClient.getStreamUrl(context, videoId)"
)

with open("app/src/main/java/com/example/musicdownloader/MusicRepository.kt", "w") as f:
    f.write(content)
