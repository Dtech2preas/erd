import requests, json
url = "https://youtubei.googleapis.com/youtubei/v1/browse?key=AIzaSyD2C07e2_49XC2sT5e0M_E2"
data = {
    "context": {"client": {"clientName": "WEB", "clientVersion": "2.20230920.00.00", "hl": "en", "gl": "US"}},
    "browseId": "VLPLlqZM4covn1EbvC_6cuERQ59QaMbPkUyE"
}
headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36"
}
res = requests.post(url, json=data, headers=headers).json()
try:
    with open("playlist_res.json", "w") as f:
        json.dump(res, f, indent=2)
except Exception as e:
    print(e)
