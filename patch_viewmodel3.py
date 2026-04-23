import re

with open("app/src/main/java/com/example/musicdownloader/MusicViewModel.kt", "r") as f:
    content = f.read()

import_str = "import kotlinx.coroutines.flow.first\n"
if "import kotlinx.coroutines.flow.first" not in content:
    content = content.replace("import kotlinx.coroutines.flow.MutableStateFlow", "import kotlinx.coroutines.flow.MutableStateFlow\n" + import_str)

with open("app/src/main/java/com/example/musicdownloader/MusicViewModel.kt", "w") as f:
    f.write(content)
