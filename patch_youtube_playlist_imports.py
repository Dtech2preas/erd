import re

with open("app/src/main/java/com/example/musicdownloader/ui/YouTubePlaylistScreen.kt", "r") as f:
    content = f.read()

# Fix ArrowBack deprecation and Divider deprecation
content = content.replace("import androidx.compose.material.icons.filled.ArrowBack", "import androidx.compose.material.icons.automirrored.filled.ArrowBack")
content = content.replace("Icons.Default.ArrowBack", "Icons.AutoMirrored.Filled.ArrowBack")
content = content.replace("Divider(color = Color.DarkGray.copy(alpha = 0.5f))", "HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.5f))")

with open("app/src/main/java/com/example/musicdownloader/ui/YouTubePlaylistScreen.kt", "w") as f:
    f.write(content)
