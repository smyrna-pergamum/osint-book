# OSINT-Book for Windows — user and administrator guide

**Version 0.2.0** · Windows 10/11 (x64) · [Download](https://github.com/smyrna-pergamum/osint-book/releases/tag/web-v0.2.0) · [Changelog](../CHANGELOG.md)

The Windows edition of OSINT-Book runs as a small server on a Windows PC. You and your team use it in Edge or Chrome, on the same computer or from other computers on a trusted local network. It shares the sources, rule-based analysis and JSON format of the Android app, and adds a shared workspace, optional AI analysis and template-based reports.

## Contents
1. [Requirements](#1-requirements)
2. [Quick start](#2-quick-start)
3. [Working with investigations](#3-working-with-investigations)
4. [AI analysis](#4-ai-analysis)
5. [Reports](#5-reports)
6. [Administration](#6-administration)
7. [Languages](#7-languages)
8. [Security and privacy](#8-security-and-privacy)
9. [Configuration, backup and upgrade](#9-configuration-backup-and-upgrade)
10. [Troubleshooting](#10-troubleshooting)
11. [Build from source](#11-build-from-source)
12. [Known limitations](#12-known-limitations)

## 1. Requirements

- Windows 10 or 11, 64-bit.
- About 300 MB of disk space for the program, plus space for AI models (0.5–20 GB each, optional).
- 8 GB of RAM is enough for small CPU models. Larger models need more RAM or a GPU with Vulkan support (AMD, NVIDIA or Intel).
- Microsoft Edge (included with Windows) or Google Chrome, which the server uses to create PDF reports.
- Internet access for the OSINT queries and for downloading models. Analysis with a downloaded local model works offline.

Java and the llama.cpp engine (CPU and Vulkan builds) are included in the ZIP.

## 2. Quick start

1. Download `OSINT-Book-Web-0.2.0-Windows-x64.zip` from the [release page](https://github.com/smyrna-pergamum/osint-book/releases/tag/web-v0.2.0). You can compare its SHA-256 with the `.sha256` file published next to it. Then extract the ZIP into a permanent folder such as `C:\OSINT-Book`.
2. Start the server:
   - `Start-Local.bat`: only this computer can connect.
   - `Start-LAN.bat`: other computers on your local network can connect too. The console shows the server's IPv4 addresses. If Windows Firewall asks, allow **private networks only**.
3. On the first start, the console asks for:
   - the user-panel port (default 8080) and the admin-panel port (default 8081);
   - an administrator username and password (at least 12 characters). You can add more users right away, or later in the admin panel.
4. Open the user panel at http://localhost:8080. Other computers use `http://<server-IP>:8080`.
5. Open the admin panel at http://localhost:8081, or with `Open-Server-Management.bat`. It works only on the server computer.
6. To stop the server, press **Ctrl+C** in its console window. Other computers can connect only while the server is running.

Your data is stored in the `data` folder next to the scripts.

## 3. Working with investigations

- **Investigations.** Create one with **New investigation**, or import an Android or web JSON file with **Import JSON**. **All investigations** lists active and archived investigations; each card has **Open** and **Delete**. Inside an investigation, **Edit** renames, archives or deletes it.
- **Targets.** Add a domain, URL, IP address, organisation, GitHub repository, package or file hash. The type is detected automatically. Secret-looking URL parameters and user information are removed before anything is saved. **Delete target** removes the target's queries, findings, evidence, notes, tags and relations.
- **Queries.** **Query sources** shows which services will receive the target. Choose the sources and start. Keyed sources need a key that an administrator has added. Ping is an active check; it sends four packets from the server and needs explicit consent. Cancel buttons stop a single source, one target or every query in the investigation. Findings that already arrived are kept.
- **Findings.** Each finding has its source link, retrieval time, an evidence excerpt with SHA-256, tags, notes, a favourite flag and a review status (not reviewed, verified or conflicting).
- **Analysis tab.** It holds the rule-based analysis and timeline, and **Analyze with AI** (see [section 4](#4-ai-analysis)).
- **Map.** It shows coordinate findings on OpenStreetMap. IP coordinates are an approximate network location, not a person's location.
- **Notes and relations.** Record context and links between targets and findings. A relation you add is marked as your own inference.
- **Exports.** Under **Prepare report → Investigation export** you can download the full investigation as JSON (compatible with the Android app) or the findings as CSV.

## 4. AI analysis

AI analysis is optional. It interprets the findings you select; it never changes them or their review status. You can run models in two ways:

| | Local model | Cloud or API provider |
|---|---|---|
| Where it runs | On this server (llama.cpp, CPU or Vulkan GPU) | On the provider's servers, or on a local Ollama / LM Studio / LiteLLM server |
| Internet | Only to download the model | Needed (except for local servers) |
| Data sent out | None | The selected findings, after the user confirms |
| Cost | Free | Free tiers available; limits vary |

### 4.1 Install a local model (admin panel → AI Models)

- **This server** shows the CPU, the total and available RAM, and any GPUs the Vulkan engine detects. Integrated GPUs are marked because they share system RAM.
- **Recommended** lists instruction-tuned models that were checked with the bundled engine, with their size, download and like counts from Hugging Face and an estimate of whether each one fits this server. The highlighted model is the best match: Qwen2.5 1.5B for 8 GB of RAM, Qwen3 4B for 12–24 GB, and larger models for servers with a dedicated GPU.
- **Popular** lists the most downloaded GGUF models on Hugging Face. **Search** looks them up by name, and you can also open a repository directly by `owner/name` or its URL.
- **Choose a quantization** lists every file of a model with its size and whether it fits. Q4_K_M is a good default; higher numbers are larger and more accurate.
- **Downloads** show progress, speed and remaining time, and can be cancelled. A file is installed only after its size and SHA-256 match what Hugging Face publishes. Gated repositories, split files and files in subfolders are not supported.
- **Installed** lets you choose the device (CPU or GPU), the context size and the number of threads, then **Activate**, **Unload** or **Delete** a model. Deleting removes the file from disk. The active model is loaded again automatically when the server restarts.

As a rough guide for Q4_K_M files: models of 0.5B–1.5B parameters run on 8 GB RAM computers; 4B needs about 3.5 GB of free RAM or a GPU with 4 GB; 8B needs a GPU with 8 GB or 24 GB of RAM; 14B and 32B need dedicated GPUs with 12 GB and 24 GB.

### 4.2 Add a cloud or API provider (admin panel → AI Models → Cloud & API)

1. Pick a provider from the list. It includes Google AI Studio (Gemini), Groq, Cerebras, OpenRouter, Hugging Face Inference, Mistral, Cohere, Together AI, Fireworks, DeepInfra, SambaNova, Novita, Hyperbolic, AI/ML API and DeepSeek from [free-ai-apis](https://github.com/OuterSpacee/free-ai-apis); local Ollama, LM Studio and LiteLLM servers; and *Other OpenAI-compatible API*. Use **Get API key** to open the provider's key page.
2. Enter the API key, use **Fetch models** to pick a current model name, and **Save**.
3. Press **Test**. If the test fails, the message includes the provider's own explanation, for example an invalid key, a retired model or an exhausted quota.

The key is stored encrypted on the server and is never shown again. Internet providers must use HTTPS; plain HTTP is allowed only for this computer or for private-network addresses. Providers can be disabled or deleted at any time.

### 4.3 Analyze (user panel → investigation → Analysis)

1. Under **Analyze with AI**, choose the **AI model**. Any signed-in user can choose any installed local model or any enabled provider; a local model that is not loaded yet is loaded automatically.
2. Choose the **Output language**.
3. Select the findings (up to 100) and press **Analyze with AI**. For a cloud provider, first confirm that the selected findings may be sent to it.

The result shows:
- a **summary**;
- **evidence-linked observations**, each with a title, confidence, alternative explanations and buttons that open the cited findings with their review status;
- **contradictions** between sources. OSINT-Book itself detects differing values for the same attribute, such as two different cities for one IP address, and asks the model to explain them;
- **missing information**, **suggested verification** steps and a **risk matrix** (identity linkage, staleness, coverage).

Progress is shown while the analysis runs, and you can cancel it without unloading the model. One analysis runs on the server at a time. The latest result of each investigation is saved, and it is marked when the findings it used are edited later.

### 4.4 Large selections and small models

A local model can read only as much text as its **context size** allows (8192 tokens by default). When the selected findings do not fit, or a small model runs out of space for its answer, OSINT-Book splits the selection into parts automatically, keeping findings about the same target and attribute together. It analyzes each part and merges them into one result. To make this faster:
- select fewer findings;
- reactivate the model with a larger context (16384 or 32768, if memory allows);
- use a larger model or a cloud provider.

Small local models (up to about 4B) write best in English. For other languages, use a larger model or a cloud provider.

## 5. Reports

**Prepare report** (next to **Edit**) opens the report page:

- **Report details:** research question, report status, overall confidence, review decision, reviewer and the owner of the recommended actions. The report number (`RPR-0001`, …) is assigned once and never changes.
- **Report language:** any of the nine languages. Every heading, label and fixed text follows it. If the AI analysis is in another language, **Run AI analysis in …** repeats it with the same findings and model so the whole report uses one language.
- **Downloads:** **PDF** (created on the server with Microsoft Edge or Chrome), **HTML**, and **report JSON** in the template's data schema (`report`, `target`, `scope`, `metrics`, `sources`, `findings`, `quality`, `risks`, `recommendations`, `review`).
- **Preview, AI analysis, rule-based analysis and all query results** appear on the same page.

The report follows the *OSINT Analysis Report* template. Sources are numbered `K-001`… (one per provider and source URL, with retrieval time, an evidence hash and verification status). Findings are numbered `B-01`…: each one has the direct observation, the AI interpretation, alternative explanations, confidence and links to its sources. Without an AI analysis, the findings are listed without interpretation. Model names are not printed in the report.

## 6. Administration

The admin panel (http://localhost:8081) opens only on the server computer and only for administrators.

- **User accounts:** add users, give or remove administrator rights, disable, delete and reset passwords. You cannot delete your own account or the last administrator. All users share the investigations.
- **Password reset requests:** users can ask for a reset on the login page. Verify the person yourself, set a new password and tell them in a secure way; no e-mail is sent.
- **API keys:** add, replace or delete keys for the keyed sources. Keys are never shown again after saving.
- **Activity log:** the latest 500 account, access and change events, including every cloud AI analysis. Use **Refresh** to load new entries.
- **AI Models:** see [section 4](#4-ai-analysis).

## 7. Languages

The interface and the reports are available in English, Türkçe, Deutsch, Français, Español, Italiano, Português, Русский and العربية (right-to-left). At first the interface follows the browser's language; each user can change it with the language selector. The user panel and the admin panel remember the choice separately.

## 8. Security and privacy

- **Admin panel:** it listens only on `127.0.0.1`, so other computers cannot reach it.
- **User panel:** it uses plain HTTP. Use it only on a trusted private network, allow only private networks in Windows Firewall, and never forward its port on your router.
- **Passwords:** stored as salted PBKDF2 hashes.
- **API keys:** source and AI provider keys are encrypted with AES-GCM using `data/secrets.key`. They are never sent to the browser, and they never appear in exports or reports.
- **Queries:** a query sends only the target value to the sources you selected, along with the server's public IP address.
- **Local AI:** it runs as a separate process reachable only from this computer, with a random port and key. No investigation data leaves the server.
- **Cloud AI:** it sends only the selected findings, after the user confirms. Each one is recorded in the activity log with the provider and the model.
- **AI output:** it is an interpretation and can be wrong. The report keeps observations separate from interpretation, and the original evidence is never modified.

See also [PRIVACY.md](../PRIVACY.md).

## 9. Configuration, backup and upgrade

**Data folder (`data`).** It holds the database, `secrets.key`, `ports.properties`, `models/` (AI models), `ai-analyses/`, `reports/`, `ai-cloud.json` and `ai-engine.log`.
- **Backup:** stop the server and copy the whole `data` folder. Always keep `secrets.key` together with the database; without it, saved keys cannot be decrypted.
- **Upgrade:** stop the server and extract the new version into a new folder. Then copy the old `data` folder into it, or point `OSINT_DATA` to the old folder, and start the new version.

**Environment variables (optional):**

| Variable | Meaning |
|---|---|
| `OSINT_DATA` | Path to the data folder |
| `OSINT_PORT` / `OSINT_ADMIN_PORT` | Ports used on the first start (default 8080 / 8081). They are then saved in `data/ports.properties`; edit that file to change them later. |
| `OSINT_USERNAME` / `OSINT_PASSWORD` | Create the first administrator without prompts (only when no accounts exist) |
| `OSINT_KEY_<PROVIDER>` | A source API key supplied from the environment, e.g. `OSINT_KEY_VIRUSTOTAL` |
| `OSINT_PDF_BROWSER` | Path to another Chromium-based browser for PDF creation |

## 10. Troubleshooting

| Problem | What to do |
|---|---|
| The server does not start: *port in use* | Choose other ports in `data/ports.properties`, or stop the program that uses them. |
| Other computers cannot connect | Use `Start-LAN.bat`, allow the private network in Windows Firewall, and use the server's IPv4 address with the user port. |
| The model does not load | The panel shows the reason from `data/ai-engine.log`. Usually there is not enough RAM: choose a smaller model or quantization, close other programs, or use the GPU. |
| *The selected findings do not fit* or the analysis is slow | See [4.4](#44-large-selections-and-small-models). |
| A cloud provider's **Test** fails | Read the provider message shown after the error. Check the key, use **Fetch models** to choose a current model name, or wait for the free quota to reset. |
| The PDF cannot be created | Install or repair Microsoft Edge, or download the HTML report and print it to PDF. |
| Unexpected *Operation failed* | The server console shows the full error. |

## 11. Build from source

Install JDK 21 or later, then run from the repository root:

```powershell
.\web\Build.ps1 -BundleRuntime
```

The script downloads the two llama.cpp builds once and checks their SHA-256, runs the tests, adds a trimmed Java runtime and creates `web/build/distributions/OSINT-Book-Web-<version>-Windows-x64.zip` with a `.sha256` file. User data is never included. The Android SDK is not needed. When you start the server from the source tree with `web\scripts\Start-Local.bat`, it stores data in `web\data`.

## 12. Known limitations

- Cloud providers have only been tested against an OpenAI-compatible endpoint, not with live keys for every provider. Free tiers and model names change often.
- Small local models produce short, less precise analyses, and they are weak in languages other than English.
- On a CPU, an analysis takes from a few to many minutes, depending on the model and the number of findings.
- NVIDIA GPUs run through Vulkan; there is no CUDA-specific build or multi-GPU tuning.
- AI results and report settings are not part of the Android-compatible JSON export.
