# OSINT-Book

Android için ücretsiz, açık kaynak, yerel öncelikli OSINT (açık kaynak istihbarat) uygulaması.
Domain, URL, IP adresi, kurum, GitHub deposu, yazılım paketi ve dosya özeti (hash) hakkında kamuya açık
bilgileri seçtiğiniz kaynaklardan toplar, kaynak bağlantısı ve erişim zamanıyla saklar, PDF/CSV/JSON olarak raporlar.

> Uygulama kimliği: `io.github.smyrna_pergamum.osintbook` (GitHub hesabına dayanır, şartname FD-02). İlk yayından sonra değiştirilmemelidir.

## Özellikler
- Araştırma dosyaları: oluşturma, yeniden adlandırma, kopyalama, arşivleme, silme, yerel arama
- Hedef türleri: Domain (IDN), URL (gizli parametre uyarısı), IPv4/IPv6, Kurum, Depo/hesap, Paket, Hash
- 22 kaynak modülü (bkz. [docs/API_REGISTRY.md](docs/API_REGISTRY.md)). Anahtarsız olanlar hemen çalışır, anahtarlılar kullanıcının kendi ücretsiz anahtarıyla
- Dört kaynaktan IP konumu (ipwho.is, ipapi.co ve GeoJS anahtarsız; BigDataCloud anahtarlı), OpenStreetMap haritasında işaretleme, enlem/boylam, Nominatim ile şehir düzeyinde yaklaşık adres
- Ping (aktif kontrol, varsayılan olarak kapalı)
- Kural tabanlı analiz: domain yaşı ve kayıt durumu, SPF/DMARC/CAA/DNSSEC, CDN izleri, IP konum tutarlılığı, sağlayıcı bazında tehdit bulguları, ilişki ve hedef önerileri, zaman çizelgesi, eksik kaynaklar. Uygulamada "Analiz" sekmesinde ve raporun en üstünde; genel risk puanı yoktur
- Sorguya göre gruplanmış bulgular; inceleme durumu, favori, etiket, not, ilişki (kaynakta belirtilen / çıkarım)
- Rapor: PDF önizleme; PDF/CSV/JSON kaydetme (SAF) ve paylaşma (FileProvider); metin özeti paylaşma
- JSON içe aktarma (boyut ve kayıt sınırları, yeni kimlikler, otomatik sorgu yok)
- 9 dil (Türkçe, English, Deutsch, Français, Español, Italiano, Português, Русский, العربية); açık/koyu tema
- Hesap, reklam, izleme veya telemetri yok. API anahtarları Android Keystore (AES-GCM) ile şifreli tutulur; veriler yedeklemeye alınmaz

## Kurulum
Henüz F-Droid'de değil. Kaynaktan derlemek için aşağıdaki talimatları izleyin.

## Derleme
Bkz. [BUILDING.md](BUILDING.md).

## Lisans
GPL-3.0-or-later. Bkz. [LICENSE](LICENSE) ve [THIRD_PARTY.md](THIRD_PARTY.md).
