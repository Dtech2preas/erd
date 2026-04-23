import re

with open("app/src/main/java/com/example/musicdownloader/ui/SearchScreen.kt", "r") as f:
    content = f.read()

# Move selectedTab up
content = content.replace("    var selectedTab by remember { mutableStateOf(0) } // 0 = Songs, 1 = Playlists\n", "")
content = content.replace("    var query by remember { mutableStateOf(initialQuery ?: \"\") }", "    var query by remember { mutableStateOf(initialQuery ?: \"\") }\n    var selectedTab by remember { mutableStateOf(0) } // 0 = Songs, 1 = Playlists")

with open("app/src/main/java/com/example/musicdownloader/ui/SearchScreen.kt", "w") as f:
    f.write(content)
