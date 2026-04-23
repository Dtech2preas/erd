import re

with open("app/src/main/java/com/example/musicdownloader/MusicRepository.kt", "r") as f:
    content = f.read()

funcs = """
    suspend fun searchPlaylists(context: Context, query: String): Result<List<PlaylistItem>> {
        AppLogger.log("[Repo] searchPlaylists called for: '$query'")
        try {
            val items = InnerTubeClient.searchPlaylists(query)
            return Result.success(items)
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.failure(e)
        }
    }

    suspend fun getPlaylistVideos(context: Context, playlistId: String): Result<List<VideoItem>> {
        AppLogger.log("[Repo] getPlaylistVideos called for: '$playlistId'")
        try {
            val items = InnerTubeClient.getPlaylistVideos(playlistId)
            return Result.success(items)
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.failure(e)
        }
    }
"""

# Insert before `suspend fun getStreamUrl`
content = content.replace("    suspend fun getStreamUrl", funcs + "\n    suspend fun getStreamUrl")

with open("app/src/main/java/com/example/musicdownloader/MusicRepository.kt", "w") as f:
    f.write(content)
