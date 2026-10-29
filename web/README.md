# OSINT-Book Web 0.2 — Windows and local networks

Run OSINT-Book in Edge or Chrome using a Windows server. The interface starts in English; English, Turkish and German are available through the language selector. All users share the same investigations.

## Start the Windows package

1. Extract OSINT-Book-Web-Windows.zip into a permanent folder.
2. Run Start-Local.bat for this computer, or Start-LAN.bat for other computers on your local network.
3. On the first start, choose the user and admin ports (defaults 8080 and 8081), then create an administrator username and a password of at least 12 characters in the console. Answer `y` to add additional users and choose whether each is an administrator; answer `n` or press Enter to finish. Accounts persist across restarts.
4. Open the user panel address shown in the console, for example http://localhost:8080. LAN clients use the server IPv4 address and the chosen user port. Sign in with your username and password.
5. Stop the server with Ctrl+C. The server computer must remain running for other computers to connect.

The bundled package includes Java. LAN connections use HTTP: use a trusted private network. If Windows Firewall requests permission, allow only the private network. Do not expose this server directly to the internet.

## Users and API keys

Administrators use the separate admin panel to add accounts, change administrator permissions, disable or delete accounts and reset passwords. Deleting an account revokes its sessions and removes pending password reset requests. Shared investigation data and audit logs remain; an administrator cannot delete their own account or the last enabled administrator. The last enabled administrator cannot be disabled. Accounts share investigation data; there is no per-user investigation isolation.

Start the server and open `Open-Server-Management.bat` on that computer, or open the admin panel address displayed in the console (default http://localhost:8081). The admin port binds to 127.0.0.1 and is accessible only from the server computer. Sign in as an administrator to manage accounts, roles, reset requests, API keys and the latest 500 activity log entries. The user panel does not expose admin APIs. Accounts and logs remain on the server.

Users who cannot sign in can select **Request password reset** on the user login page. The administrator must independently verify the requester and set a new password in the admin panel, then communicate it securely. Existing sessions for that account are revoked. The request does not send an email or automatically reset a password.

To delete a target, open its investigation, choose **Delete target** and confirm. Its query history, findings, evidence, linked notes, tags and relations are permanently deleted. Cancel and wait for active queries before deleting. General investigation notes and unrelated targets remain intact.

First-start account setup runs only when the server has no accounts. For unattended setup, `OSINT_USERNAME` and `OSINT_PASSWORD` create one administrator without interactive prompts; add further users later in Settings.

The admin panel lets administrators add, replace and delete stored provider keys. Key values are never returned to the browser. Stored keys use AES-GCM encryption; passwords use salted PBKDF2 hashes. Back up the entire data directory, including secrets.key, which is required to decrypt saved API keys. Protect access to this directory.

Environment keys named OSINT_KEY_PROVIDER remain supported. Deleting a stored key does not remove an environment-provided key.

## Queries, maps and cancellation

Ping runs Windows ping on the server, sends four packets, and requires explicit active-query consent. A blocked ping response does not prove a host is offline.

The Map tab displays coordinate findings. Load map contacts OpenStreetMap; IP coordinates indicate an approximate network location, not a person's location. Stop map removes the embedded map.

Cancel buttons stop individual providers, target queries or all investigation queries. Forms can be dismissed and pending browser requests can be aborted. Cancelling a browser request does not undo a save already completed on the server; refresh to check the result. Successful findings remain available after query cancellation.

Research, targets, evidence review, notes, tags, relations, JSON import/export, CSV export and browser Print / Save as PDF are supported. The rule-based analysis is included alongside the optional local AI analysis described below.

## Local AI analysis

OSINT-Book can interpret selected findings with an open language model that runs on the server itself. Investigation data is never sent to Hugging Face or any other online service. After a model is downloaded, analysis works without internet access.

The package includes two builds of the [llama.cpp](https://github.com/ggml-org/llama.cpp) inference engine (release b11934, SHA-256 pinned in `Build.ps1`): `ai-runtime` for CPUs and `ai-runtime-gpu` (Vulkan) for AMD, NVIDIA and Intel GPUs. No model weights are bundled.

### Choose and download a model (admin panel → AI Models)

- **This server** shows CPU threads, total and available RAM, and GPUs detected by the Vulkan engine. Integrated GPUs are marked because they share system RAM.
- **Recommended** lists instruction-tuned models checked with the bundled engine, with live size, download and like counts from Hugging Face and an estimate of whether each fits this server. The highlighted model is the best match for the hardware: Qwen2.5 1.5B on 8 GB RAM computers, Qwen3 4B on 12–24 GB RAM, larger models on servers with a dedicated GPU.
- **Popular** lists the most downloaded GGUF text-generation repositories on Hugging Face. **Search** finds repositories by name, or opens a repository by `owner/name` or `https://huggingface.co/owner/name`.
- **Choose a quantization** shows every GGUF file in a repository with its quantization (for example Q4_K_M), download size and fit for this server. Q4_K_M is a good default.
- Downloads show real progress, speed and remaining time, and can be cancelled. The file is checked against the size and SHA-256 published by Hugging Face before it is installed; a partial or mismatching file is deleted. Only one download runs at a time. Gated or private repositories, split (`-00001-of-0000N`) files, files in subfolders and vision projector files are not supported.
- Models are stored in `data/models` with a manifest recording repository, revision, file name, size and SHA-256.

### Activate, unload or delete (Installed)

Choose the device (CPU, or GPU when one is detected), context size and CPU threads, then **Activate**. The engine starts as a separate process bound to 127.0.0.1 with a random port and key, so it is never reachable from the network. GPU mode offloads as many layers as fit in GPU memory and keeps the rest on the CPU. The active model is loaded again automatically when the server restarts. **Unload** stops the engine; **Delete** removes an unloaded model from disk. If loading fails, the panel shows the reason from the engine log (`data/ai-engine.log`), such as insufficient memory.

Rough guide with Q4_K_M files: 0.5B–1.5B models run on 8 GB RAM computers; 4B needs about 3.5 GB free RAM or a 4 GB GPU; 8B needs a GPU with 8 GB memory or a server with 24 GB+ RAM; 14B and 32B need dedicated GPUs with 12 GB and 24 GB memory. On CPUs, expect roughly 1–5 minutes per analysis depending on model size and the number of findings.

### Analyze with AI (user panel → investigation → Analysis)

Select up to 100 findings and choose **Analyze with AI**. Only those findings, with their source, retrieval time, verification status and evidence excerpts, are sent to the local model. The result shows:

- an English **summary**;
- **evidence-linked observations**, each with buttons that open the cited findings and show their verification status;
- **contradictions** between sources (the application also detects differing values for the same attribute, such as two IP geolocation cities, and asks the model to explain them);
- **missing information** and **suggested verification** steps.

Findings are given to the model as references F1, F2, … and mapped back to the real findings; references the model invents are removed and counted. Generation is constrained to a JSON schema. Progress shows the tokens read and written, and the analysis can be cancelled without unloading the model. One analysis runs at a time on the server.

The original findings, evidence and verification statuses are never changed. The latest result for each investigation is saved in `data/ai-analyses` and marked when the findings it used are later edited or deleted. AI results are interpretations, can be wrong, and are not included in JSON/CSV exports or the Android-compatible format.

Limitations: small models produce shorter and less precise analysis; if the selected findings do not fit the context size, select fewer findings or reactivate the model with a larger context. CUDA-specific and multi-GPU tuning are not provided; NVIDIA GPUs use the Vulkan build.

## Upgrade and backup

Stop the old server and back up the entire data directory. Extract the new package separately and copy the data directory into the new osint-book-web folder, or set OSINT_DATA to its existing absolute path. Keep secrets.key with the database. Start the new server. When upgrading from the shared-password version, create the first named administrator; investigation data is retained.

## Server configuration

- OSINT_DATA: data directory (the launch scripts default to the package's data folder).
- OSINT_PORT: optional user-port override (default 8080).
- OSINT_ADMIN_PORT: optional administrator-port override (default 8081, localhost only).
- On first start the ports are saved in `data/ports.properties`. To change them, stop the server and edit that file, or set the two environment variables before starting. Ports must be distinct and between 1 and 65535. If a port is already in use, choose another one. Environment overrides do not change an existing saved file.
- OSINT_USERNAME and OSINT_PASSWORD: optional first-account bootstrap values. They do not reset existing accounts.
- OSINT_KEY_PROVIDER: optional provider API keys, using the provider's uppercase identifier.

## Build from source

Install JDK 21 or later and set JAVA_HOME. From the repository root, run:

```powershell
.\web\Build.ps1 -BundleRuntime
```

The build downloads the two llama.cpp runtimes once (checksums verified), runs tests and creates web/build/distributions/OSINT-Book-Web-Windows.zip. Android SDK is not needed for this web module. The distribution excludes runtime user data.
