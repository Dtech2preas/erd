import re

with open("app/src/main/java/com/example/musicdownloader/ui/SearchScreen.kt", "r") as f:
    content = f.read()

# Modify constructor arguments to support navigating to playlist details
content = content.replace(
    "onViewDownloads: () -> Unit",
    "onViewDownloads: () -> Unit,\n    onPlaylistClick: (String, String) -> Unit = { _, _ -> }"
)

# Add state for current tab
state_insert = """
    var selectedTab by remember { mutableStateOf(0) } // 0 = Songs, 1 = Playlists
"""
content = content.replace("val context = LocalContext.current\n", "val context = LocalContext.current\n" + state_insert)

# Modify search trigger
search_trigger_old = "viewModel.search(query)"
search_trigger_new = "if (selectedTab == 0) viewModel.search(query) else viewModel.searchPlaylists(query)"
content = content.replace("IconButton(onClick = { viewModel.search(query) }) {", "IconButton(onClick = { " + search_trigger_new + " }) {")
content = content.replace("onSearch = { viewModel.search(query) }", "onSearch = { " + search_trigger_new + " }")

# Insert Tabs
tabs_str = """
            Spacer(modifier = Modifier.height(16.dp))
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = ElectricPurple,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = ElectricPurple
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0; if (query.isNotBlank()) viewModel.search(query) },
                    text = { Text("Songs", color = if (selectedTab == 0) ElectricPurple else Color.Gray) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1; if (query.isNotBlank()) viewModel.searchPlaylists(query) },
                    text = { Text("Playlists/Albums", color = if (selectedTab == 1) ElectricPurple else Color.Gray) }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
"""
content = content.replace("                keyboardActions = KeyboardActions(\n                    onSearch = { if (selectedTab == 0) viewModel.search(query) else viewModel.searchPlaylists(query) }\n                )\n            )", "                keyboardActions = KeyboardActions(\n                    onSearch = { if (selectedTab == 0) viewModel.search(query) else viewModel.searchPlaylists(query) }\n                )\n            )\n" + tabs_str)

# Modify Results section
results_old = """
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ElectricPurple)
                }
            } else if (uiState.errorMessage != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "Error: ${uiState.errorMessage}", color = Color.Red)
                }
            } else {
                LazyColumn(
                    contentPadding = contentPadding,
                    modifier = Modifier.weight(1f)
                ) {
                    items(uiState.results) { video ->
                        val subtitle = if (video.album != null && video.album != "Unknown Album") "${video.uploader} • ${video.album}" else video.uploader
                        MusicRowItem(
                            title = video.title,
                            subtitle = subtitle,
                            thumbnailUrl = video.thumbnailUrl,
                            isLibrary = false,
                            isDownloaded = downloadedIds.contains(video.id),
                            downloadProgress = downloadProgress[video.id]?.progress,
                            isWaiting = initializingDownloads.contains(video.id),
                            isCached = cachedStreamIds.contains(video.id),
                            onClick = {
                                if (downloadedIds.contains(video.id)) {
                                    viewModel.playSong(video.id, video.title, video.uploader, video.thumbnailUrl)
                                } else {
                                    viewModel.playStream(video)
                                }
                            },
                            onDownload = { viewModel.downloadSong(video) },
                            onCancelDownload = { viewModel.cancelDownload(video.id) }
                        )
                    }
                }
            }
"""

results_new = """
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ElectricPurple)
                }
            } else if (uiState.errorMessage != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "Error: ${uiState.errorMessage}", color = Color.Red)
                }
            } else {
                if (selectedTab == 0) {
                    LazyColumn(
                        contentPadding = contentPadding,
                        modifier = Modifier.weight(1f)
                    ) {
                        items(uiState.results) { video ->
                            val subtitle = if (video.album != null && video.album != "Unknown Album") "${video.uploader} • ${video.album}" else video.uploader
                            MusicRowItem(
                                title = video.title,
                                subtitle = subtitle,
                                thumbnailUrl = video.thumbnailUrl,
                                isLibrary = false,
                                isDownloaded = downloadedIds.contains(video.id),
                                downloadProgress = downloadProgress[video.id]?.progress,
                                isWaiting = initializingDownloads.contains(video.id),
                                isCached = cachedStreamIds.contains(video.id),
                                onClick = {
                                    if (downloadedIds.contains(video.id)) {
                                        viewModel.playSong(video.id, video.title, video.uploader, video.thumbnailUrl)
                                    } else {
                                        viewModel.playStream(video)
                                    }
                                },
                                onDownload = { viewModel.downloadSong(video) },
                                onCancelDownload = { viewModel.cancelDownload(video.id) }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = contentPadding,
                        modifier = Modifier.weight(1f)
                    ) {
                        items(uiState.playlistResults) { playlist ->
                            MusicRowItem(
                                title = playlist.title,
                                subtitle = playlist.songCountText,
                                thumbnailUrl = playlist.thumbnailUrl,
                                isLibrary = false,
                                isDownloaded = false,
                                downloadProgress = null,
                                isWaiting = false,
                                isCached = false,
                                onClick = {
                                    onPlaylistClick(playlist.id, playlist.title)
                                },
                                onDownload = { },
                                onCancelDownload = { },
                                showDownloadButton = false
                            )
                        }
                    }
                }
            }
"""

content = content.replace(results_old, results_new)

# Fix search trigger issue on initial load
content = content.replace("""        if (!initialQuery.isNullOrBlank()) {
            query = initialQuery
            viewModel.search(initialQuery)
        }""", """        if (!initialQuery.isNullOrBlank()) {
            query = initialQuery
            if (selectedTab == 0) viewModel.search(initialQuery) else viewModel.searchPlaylists(initialQuery)
        }""")

# Provide tabIndicatorOffset import
import_insert = "import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset\n"
if "tabIndicatorOffset" not in content[:500]:
    content = content.replace("import androidx.compose.material3.*", "import androidx.compose.material3.*\n" + import_insert)

with open("app/src/main/java/com/example/musicdownloader/ui/SearchScreen.kt", "w") as f:
    f.write(content)
