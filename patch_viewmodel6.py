import re

with open("app/src/main/java/com/example/musicdownloader/MusicViewModel.kt", "r") as f:
    content = f.read()

# Fix MusicUiState
uistate_re = r'(data class MusicUiState\(\n.*?val madeForYou: List<VideoItem> = emptyList\(\))(.*?\n\))'
def uistate_replacer(match):
    # Check if we already added it earlier (I accidentally targeted HomeUiState before, but it is MusicUiState)
    return match.group(1) + ",\n    val playlistResults: List<com.example.musicdownloader.PlaylistItem> = emptyList(),\n    val playlistVideos: List<com.example.musicdownloader.VideoItem> = emptyList()\n)"
content = re.sub(uistate_re, uistate_replacer, content, flags=re.DOTALL)

# Fix first() not finding list elements
content = content.replace("val allPlaylists = kotlinx.coroutines.flow.first(playlistDao.getAllPlaylists())", "val allPlaylists = playlistDao.getAllPlaylists().first()")
content = content.replace("allPlaylists.find { it.name == localPlaylistName }?.id", "allPlaylists.find { it.name == localPlaylistName }?.id")

with open("app/src/main/java/com/example/musicdownloader/MusicViewModel.kt", "w") as f:
    f.write(content)
