# API registry (API-01, DEL-03)

Last checked: 2026-10-27. Quotas and terms change; re-check before every release.
Single source of truth in code: `app/src/main/java/io/github/smyrna_pergamum/osintbook/providers/` (adapters, `Providers.all`, quota fields).

| ID | Provider | Target types | Auth | Free quota (as stated) | Official docs | Notes |
|---|---|---|---|---|---|---|
| google_dns | Google Public DNS JSON | Domain, URL | None | Not stated | https://developers.google.com/speed/public-dns/docs/doh/json | Also fetches the `_dmarc` TXT record and the DNSSEC (AD) flag |
| rdap | IANA RDAP bootstrap (data.iana.org) → responsible registry | Domain, URL, IP | None | Per registry | https://www.iana.org/assignments/rdap-dns/rdap-dns.xhtml | No intermediary; TLDs without public RDAP (e.g. .tr, .de) return no registration data. Personal contact fields are not collected |
| wayback | Internet Archive | Domain, URL | None | Not stated | https://archive.org/help/wayback_api.php | |
| github | GitHub REST | Repository, organisation | Optional token | 60/hour/IP; 5,000/hour with a token | https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api | |
| wikipedia | Wikipedia + Wikidata | Organisation, domain | None | Not stated | https://www.mediawiki.org/wiki/API:Main_page | CC BY-SA / CC0 attribution |
| osv | OSV.dev | Package | None | Not stated | https://google.github.io/osv.dev/api/ | Version ambiguity is shown |
| virustotal | VirusTotal v3 | Domain, URL, IP, hash | User key | 4/min, 500/day, non-commercial | https://docs.virustotal.com/reference/public-vs-premium-api | Read only, nothing uploaded |
| abuseipdb | AbuseIPDB v2 | IP | User key | 1,000/day | https://docs.abuseipdb.com/ | |
| otx | AlienVault OTX | Domain, URL, IP | User key | Not stated | https://otx.alienvault.com/assets/static/external_api.html | |
| urlhaus | abuse.ch URLhaus | Domain, URL, IP | User Auth-Key | Not stated | https://urlhaus-api.abuse.ch/ | Lookup only |
| malwarebazaar | abuse.ch MalwareBazaar | Hash | User Auth-Key | Not stated | https://bazaar.abuse.ch/api/ | No sample download or upload |
| safebrowsing | Google Safe Browsing v4 Lookup | Domain, URL | User key | Non-commercial use | https://developers.google.com/safe-browsing/v4/lookup-api | Key sent in a header |
| urlscan | urlscan.io search | Domain, URL, IP | Optional key | Low limit without a key | https://urlscan.io/docs/api/ | Never submits new scans |
| ipwhois | ipwho.is | IP | None | Non-commercial use | https://ipwhois.io/documentation | Approximate location |
| ipapico | ipapi.co | IP | None | ~1,000/day | https://ipapi.co/api/ | Approximate location |
| geojs | GeoJS | IP | None | Not stated | https://www.geojs.io/docs/v1/endpoints/geo/ | Accuracy radius shown |
| bigdatacloud | BigDataCloud IP Geolocation | IP | User key (URL parameter) | Monthly free quota | https://www.bigdatacloud.com/docs/api/ip-geolocation-api | Confidence level; key never stored in saved URLs |
| nominatim | OSM Nominatim (reverse geocoding) | IP coordinates | None | Max. 1 request/s | https://operations.osmfoundation.org/policies/nominatim/ | Started manually, city level |
| ping | System ping tool | Domain, URL, IP | None | 4 requests | — | Active, off by default |
| (map) | tile.openstreetmap.org | — | None | Tile Usage Policy | https://operations.osmfoundation.org/policies/tiles/ | Only while a map is shown or a report map is built |

## Evaluated and not included
- Services that upload files or detonate samples (CAPE, Malwagon, Scanii, MalShare, MalDatabase): out of scope under SEC-10 and §1.
- Paid, closed or unclear services (Web of Trust, NoPhishy, Phisherman, IPWhois.net Blacklist).
- ip-api.com: the free tier is HTTP only (blocked by SEC-06).
- Phone-number lookup APIs: personal data; person profiling is out of scope in the specification.
