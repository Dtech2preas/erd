import json

with open("playlist_res.json", "r") as f:
    data = json.load(f)

videos = []
try:
    tabs = data.get("contents", {}).get("twoColumnBrowseResultsRenderer", {}).get("tabs", [])
    if not tabs:
        print("No tabs found")
        exit(1)

    contents = tabs[0].get("tabRenderer", {}).get("content", {}).get("sectionListRenderer", {}).get("contents", [])
    for c in contents:
        items = c.get("itemSectionRenderer", {}).get("contents", [])
        for item in items:
            playlist_items = item.get("playlistVideoListRenderer", {}).get("contents", [])
            for p_item in playlist_items:
                if "playlistVideoRenderer" in p_item:
                    renderer = p_item["playlistVideoRenderer"]
                    videoId = renderer.get("videoId")
                    title = renderer.get("title", {}).get("runs", [{}])[0].get("text", "Unknown")
                    lengthText = renderer.get("lengthText", {}).get("simpleText", "")
                    uploader = renderer.get("shortBylineText", {}).get("runs", [{}])[0].get("text", "Unknown")
                    videos.append({"id": videoId, "title": title, "duration": lengthText, "uploader": uploader})

    print(json.dumps(videos, indent=2)[:500])
except Exception as e:
    print("Error:", e)
