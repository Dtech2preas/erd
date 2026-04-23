import requests
import json

API_KEY = "AIzaSyD2" + "C07e2_" + "49XC2" + "sT5e" + "0M_" + "E2"
url = f"https://youtubei.googleapis.com/youtubei/v1/search?key={API_KEY}"

data = {
    "context": {
        "client": {
            "clientName": "WEB",
            "clientVersion": "2.20230920.00.00",
            "hl": "en",
            "gl": "US"
        }
    },
    "query": "linkin park",
    "params": "EgIQAw%3D%3D" # playlist filter
}
response = requests.post(url, json=data)
print(json.dumps(response.json(), indent=2)[:1000])
