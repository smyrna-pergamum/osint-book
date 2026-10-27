# API kayıt dosyası (API-01, DEL-03)

Son kontrol: 2026-10-26. Kotalar ve koşullar değişebilir; her sürümden önce yeniden kontrol edin.
Kod tarafındaki tek kaynak: `app/src/main/java/io/github/smyrna_pergamum/osintbook/providers/` (adaptörler, `Providers.all`, kota alanları).

| Kimlik | Sağlayıcı | Hedef türleri | Yetki | Ücretsiz kota (bildirilen) | Resmî doküman | Not |
|---|---|---|---|---|---|---|
| google_dns | Google Public DNS JSON | Domain, URL | Yok | Belirtilmemiş | https://developers.google.com/speed/public-dns/docs/doh/json | Google gizlilik koşulları |
| rdap | IANA RDAP listesi (data.iana.org) → ilgili kayıt kuruluşu | Domain, URL, IP | Yok | Kayıt kuruluşuna göre | https://www.iana.org/assignments/rdap-dns/rdap-dns.xhtml | Aracı servis yok; RDAP sunmayan uzantılar (ör. .tr, .de) için kayıt bilgisi alınamaz |
| wayback | Internet Archive | Domain, URL | Yok | Belirtilmemiş | https://archive.org/help/wayback_api.php | |
| github | GitHub REST | Depo, Kurum | İsteğe bağlı token | 60/saat/IP; token ile 5000/saat | https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api | |
| wikipedia | Wikipedia + Wikidata | Kurum, Domain | Yok | Belirtilmemiş | https://www.mediawiki.org/wiki/API:Main_page | CC BY-SA / CC0 atıf |
| osv | OSV.dev | Paket | Yok | Belirtilmemiş | https://google.github.io/osv.dev/api/ | Sürüm belirsizliği gösterilir |
| virustotal | VirusTotal v3 | Domain, URL, IP, Hash | Kullanıcı anahtarı | 4/dk, 500/gün, ticari olmayan | https://docs.virustotal.com/reference/public-vs-premium-api | Yalnızca okuma, yükleme yok |
| abuseipdb | AbuseIPDB v2 | IP | Kullanıcı anahtarı | 1000/gün | https://docs.abuseipdb.com/ | |
| otx | AlienVault OTX | Domain, URL, IP | Kullanıcı anahtarı | Belirtilmemiş | https://otx.alienvault.com/assets/static/external_api.html | |
| urlhaus | abuse.ch URLhaus | Domain, URL, IP | Kullanıcı Auth-Key | Belirtilmemiş | https://urlhaus-api.abuse.ch/ | Yalnızca sorgu |
| malwarebazaar | abuse.ch MalwareBazaar | Hash | Kullanıcı Auth-Key | Belirtilmemiş | https://bazaar.abuse.ch/api/ | Örnek indirme/yükleme yok |
| safebrowsing | Google Safe Browsing v4 Lookup | Domain, URL | Kullanıcı anahtarı | Ticari olmayan kullanım | https://developers.google.com/safe-browsing/v4/lookup-api | Anahtar başlıkta gönderilir |
| urlscan | urlscan.io arama | Domain, URL, IP | İsteğe bağlı anahtar | Anahtarsız düşük limit | https://urlscan.io/docs/api/ | Yeni tarama gönderilmez |
| ipwhois | ipwho.is | IP | Yok | Ticari olmayan kullanım | https://ipwhois.io/documentation | Yaklaşık konum |
| ipapico | ipapi.co | IP | Yok | ~1000/gün | https://ipapi.co/api/ | Yaklaşık konum |
| geojs | GeoJS | IP | Yok | Belirtilmemiş | https://www.geojs.io/docs/v1/endpoints/geo/ | Doğruluk yarıçapı gösterilir |
| bigdatacloud | BigDataCloud IP Geolocation | IP | Kullanıcı anahtarı (URL parametresi) | Aylık ücretsiz kota | https://www.bigdatacloud.com/docs/api/ip-geolocation-api | Güven düzeyi; anahtar kaydedilen URL'de yer almaz |
| nominatim | OSM Nominatim (ters kodlama) | IP koordinatı | Yok | En fazla 1 istek/sn | https://operations.osmfoundation.org/policies/nominatim/ | Elle başlatılır, şehir düzeyi |
| ping | Sistem ping aracı | Domain, URL, IP | Yok | 4 istek | — | Aktif, varsayılan kapalı |
| (harita) | tile.openstreetmap.org | — | Yok | Tile Usage Policy | https://operations.osmfoundation.org/policies/tiles/ | Yalnızca harita ekrandayken |

## Değerlendirilip alınmayanlar
- Dosya yükleyen veya örnek çalıştıran servisler (CAPE, Malwagon, Scanii, MalShare, MalDatabase): SEC-10 ve §1 kapsam dışı.
- Ücretli, kapalı veya belirsiz servisler (Web of Trust, NoPhishy, Phisherman, IPWhois.net Blacklist).
- ip-api.com: ücretsiz katmanı yalnızca HTTP (SEC-06 ile engellenir).
- Telefon numarası sorgulama API'leri: kişisel veri; şartnamede kişi profilleme kapsam dışıdır.
