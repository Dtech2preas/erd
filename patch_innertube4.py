import re

with open("app/src/main/java/com/example/musicdownloader/InnerTubeClient.kt", "r") as f:
    content = f.read()

content = content.replace('playlistRenderer.optJSONObject("thumbnails")?.optJSONArray(0)?.optJSONArray("thumbnails")', 'playlistRenderer.optJSONArray("thumbnails")?.optJSONObject(0)?.optJSONArray("thumbnails")')

with open("app/src/main/java/com/example/musicdownloader/InnerTubeClient.kt", "w") as f:
    f.write(content)
