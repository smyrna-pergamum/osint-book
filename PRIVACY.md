# Privacy

OSINT-Book has no account system of its own, no analytics, no advertising and no telemetry. The developer receives no data from either edition.

## Android app

- **Stored locally:** investigations, targets, findings, notes, tags, relations. Raw responses are optional (off by default, kept at most 30 days).
- **Encrypted:** API keys, with an AES-GCM key held in Android Keystore. The database itself is not additionally encrypted.
- **Backups:** all app data is excluded from cloud backup and device-to-device transfer.
- **Network:** nothing is sent on first launch or during local-only use. When you start a query, only the target value that source needs and your device's IP address reach the provider you chose. Before each query the app shows what goes where.
- **Ping:** an active check; packets go straight to the target. It is never preselected.
- **Maps:** while a map is on screen, map tiles for that area load from tile.openstreetmap.org. Reports with a map download those tiles once. "Find approximate address" sends coordinates to nominatim.openstreetmap.org.
- **Analysis:** computed on the device from stored findings; it makes no network request.
- **Never collected:** contacts, SMS, location, microphone, files, clipboard, browsing history, analytics, advertising ID.
- **Export:** files are only written where you save them or sent to the app you share with, and never contain API keys. Deleting an investigation does not delete files you exported.

## Windows edition

- **Stored on your server:** everything lives in the `data` folder on the computer that runs the server. This covers accounts, investigations, AI analyses, report settings, downloaded models and the activity log. All users of the server share the investigations.
- **Encrypted:** source and AI provider API keys, with AES-GCM and a key in `data/secrets.key`. Passwords are stored as salted PBKDF2 hashes. Keys are never sent to the browser, and they never appear in exports or reports.
- **Network for queries:** the selected sources receive the target value and the server's public IP address, as on Android.
- **Model downloads:** AI model lists and downloads contact huggingface.co. Only search text and model names are sent; no investigation data is.
- **Local AI:** the model runs on the server and is reachable only from that computer. Analysis sends nothing over the network.
- **Cloud AI (optional, configured by an administrator):** the selected findings are sent to the chosen provider, which handles them under its own terms. The user must confirm every time, and each cloud analysis is recorded in the activity log with the user, the provider and the model.
- **PDF reports:** these are created on the server with the locally installed Microsoft Edge or Chrome, from a self-contained page that loads nothing from the internet.
- **Activity log:** sign-ins and changes are recorded with the username and time, so administrators can see who did what. Administrators can view the latest 500 entries.
- **Access:** the admin panel opens only on the server computer. The user panel uses plain HTTP and is meant for a trusted local network only.

## In both editions

IP geolocation estimates where a network is registered or routed; it is not the location of a person or device. AI output is an interpretation of the selected findings and can be wrong. Information being public does not make unlimited republishing of personal data acceptable.
