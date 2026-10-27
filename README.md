# OSINT-Book

A free, open-source, local-first OSINT (open-source intelligence) app for Android.
It collects public information about domains, URLs, IP addresses, organisations, GitHub repositories,
software packages and file hashes from sources you choose, keeps every finding with its source link and
retrieval time, and turns it into PDF, CSV or JSON reports.

> Application ID: `io.github.smyrna_pergamum.osintbook` (based on the GitHub account, spec FD-02). It must not change after the first release.

## Features
- Investigations: create, rename, copy, archive, delete, local search
- Target types: domain (IDN), URL (warns about secret parameters), IPv4/IPv6, organisation, repository/account, package, file hash
- 22 source modules (see [docs/API_REGISTRY.md](docs/API_REGISTRY.md)). Key-free sources work immediately; key-based ones use the user's own free key
- IP geolocation from four sources (ipwho.is, ipapi.co and GeoJS without a key; BigDataCloud with a key), markers on an OpenStreetMap map, latitude/longitude, city-level approximate address via Nominatim
- Ping (active check, off by default)
- Rule-based analysis: domain age and registration status, SPF/DMARC/CAA/DNSSEC, CDN hints, IP location consistency, threat findings per provider, relation and target suggestions, timeline, missing sources. Shown in the "Analysis" tab and at the top of the report; there is no overall risk score
- Findings grouped by query; review status, favourites, tags, notes, relations (stated by the source / inferred)
- Reports: PDF preview with zoom; save (Storage Access Framework) and share (FileProvider) as PDF, CSV or JSON; plain-text summary sharing
- JSON import (size and record limits, new IDs, never starts a query)
- 9 languages (English, Türkçe, Deutsch, Français, Español, Italiano, Português, Русский, العربية); light and dark theme
- No account, ads, tracking or telemetry. API keys are encrypted with Android Keystore (AES-GCM); app data is excluded from backups

## Installation
Not on F-Droid yet. Build from source with the instructions below.

## Building
See [BUILDING.md](BUILDING.md).

## Privacy
See [PRIVACY.md](PRIVACY.md).

## Licence
GPL-3.0-or-later. See [LICENSE](LICENSE) and [THIRD_PARTY.md](THIRD_PARTY.md).
