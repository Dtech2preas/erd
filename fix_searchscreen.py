import re

with open("app/src/main/java/com/example/musicdownloader/ui/SearchScreen.kt", "r") as f:
    content = f.read()

# Re-apply the TabRow changes because the original replace failed silently
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

if "TabRow(" not in content:
    content = content.replace("            Spacer(modifier = Modifier.height(16.dp))\n\n            if (uiState.isLoading) {", tabs_str + "            if (uiState.isLoading) {")

# Re-apply results list replacement
results_old = """            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ElectricPurple)
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
                            onDownloadClick = { viewModel.downloadSong(video) },

                        )
                    }
                }
            }
        }
    }
}"""

results_new = """            if (uiState.isLoading) {
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
                                onDownloadClick = { viewModel.downloadSong(video) },
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
                                onDownloadClick = { },
                                showDownloadButton = false
                            )
                        }
                    }
                }
            }
        }
    }
}"""

content = content.replace(results_old, results_new)

with open("app/src/main/java/com/example/musicdownloader/ui/SearchScreen.kt", "w") as f:
    f.write(content)
