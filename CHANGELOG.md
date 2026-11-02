# Changelog

## Windows / Web 0.2.2
- Analyses are queued: requests run one after another in order instead of failing with "busy"; the queue position is shown and queued analyses can be cancelled
- Small models: selections are split into parts that suit the context from the start, parts that still do not fit are split again, a single finding is retried in a compact mode, and a finding that cannot be analyzed is skipped and listed instead of failing the whole analysis
- Rate limits: providers get tokens-per-minute and requests-per-minute limits (Groq preset: 6,000 tokens, 30 requests); requests wait for the limit, 429 responses are retried after the time the provider names, and a "request too large" limit is learned and used for smaller parts. Fixes Groq's "Request too large … Limit 7000" error
- Paid AI APIs with an **Add API key** button: OpenAI, Anthropic (Claude), Azure OpenAI, xAI (Grok), Perplexity, DeepSeek
- More free providers from awesome-free-llm-apis: NVIDIA NIM, Cloudflare Workers AI, OVHcloud AI Endpoints, LLM7.io, Kilo Code, Gonka Broker, Z AI (GLM), Aion Labs, ModelScope, SiliconFlow; preset model names updated

## Windows / Web 0.2.1
- Cloud & API: the Groq preset now uses `openai/gpt-oss-120b`; Groq shut down `llama-3.3-70b-versatile` for free and developer accounts on 16 August 2026
- When a provider does not know the configured model, the error lists the chat models it currently offers
- Adding or editing a provider fetches the provider's models automatically and replaces a model name it no longer offers
- More flexible provider form: every setting of a saved provider can be edited (name, URL, model, key, limits), the model can be picked from the provider's list or typed, the connection can be tested before saving, a typed key can be shown while typing and a saved key can be removed, and advanced settings cover the key header (Bearer, api-key, x-api-key, none), extra HTTP headers, context window and maximum output

## Windows / Web 0.2.0
First release of the Windows edition (`web/`), a server used from the browser on the same PC or a trusted local network.
- Shared workspace with named accounts, roles, password reset requests, encrypted provider keys and an activity log; separate admin panel bound to localhost
- Same 22 source modules, rule-based analysis, map and Android-compatible JSON import/export as the Android app
- Local AI: Hugging Face model lists (recommended for the hardware, popular, search), quantization picker, verified downloads, bundled llama.cpp for CPU and Vulkan GPU; works offline after download
- Cloud & API providers from the free-ai-apis list, local Ollama / LM Studio / LiteLLM, or any OpenAI-compatible API; per-analysis consent and audit log
- Analyze with AI: summary, evidence-linked observations, contradictions, missing information, verification steps and risk matrix in the chosen language; large selections are split into parts automatically
- Prepare report: report following the OSINT analysis report template and data schema, as PDF, HTML or JSON, in any of 9 languages
- Delete investigations from the overview; activity log refresh
- Interface in 9 languages, including Arabic (right-to-left)

## 0.1.0 (development release)
- Investigations, targets, findings, notes, tags and relations; 22 source modules
- Rule-based analysis and timeline (Analysis tab and top of the report); DMARC and DNSSEC in the DNS module
- RDAP straight from the IANA bootstrap registry; map in PDF reports; zoomable preview; full-screen map
- IP geolocation on OpenStreetMap, approximate address via Nominatim, ping
- Findings grouped by query
- PDF/CSV/JSON reports (save and share), JSON import
- 9 languages, light/dark theme, orange theme, new icon
