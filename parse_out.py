import json

with open("out.txt", "r") as f:
    data = json.load(f)

def print_keys(obj, indent=0):
    if isinstance(obj, dict):
        for k, v in obj.items():
            print(" " * indent + k)
            if k == "contents" or k == "itemSectionRenderer" or "Renderer" in k:
                print_keys(v, indent + 2)
    elif isinstance(obj, list):
        if len(obj) > 0:
            print_keys(obj[0], indent + 2)

try:
    contents = data["contents"]["twoColumnSearchResultsRenderer"]["primaryContents"]["sectionListRenderer"]["contents"]
    for c in contents:
        print_keys(c)
        print("---")
except Exception as e:
    print("Error:", e)
