import re

with open("app/src/main/java/com/example/musicdownloader/ui/MusicRowItem.kt", "r") as f:
    content = f.read()

# Add showDownloadButton flag to constructor
content = content.replace("    onOptionClick: () -> Unit = {}", "    onOptionClick: () -> Unit = {},\n    showDownloadButton: Boolean = true")

# Find where the download button logic is and wrap it
dl_button_logic_old = """            } else {
                if (isDownloaded) {
                     Icon("""

dl_button_logic_new = """            } else if (showDownloadButton) {
                if (isDownloaded) {
                     Icon("""

content = content.replace(dl_button_logic_old, dl_button_logic_new)

with open("app/src/main/java/com/example/musicdownloader/ui/MusicRowItem.kt", "w") as f:
    f.write(content)
