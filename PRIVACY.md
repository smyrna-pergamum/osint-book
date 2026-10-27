# Gizlilik

- **Yerelde tutulanlar:** araştırmalar, hedefler, bulgular, notlar, etiketler, ilişkiler. Ham yanıtlar isteğe bağlıdır (varsayılan kapalı, en fazla 30 gün).
- **Şifrelenenler:** API anahtarları, Android Keystore'daki bir AES-GCM anahtarıyla. Veritabanı ayrıca şifrelenmez.
- **Yedekleme:** tüm uygulama verisi bulut yedeklemesinden ve cihazdan cihaza aktarımdan hariçtir.
- **Ağ:** ilk açılışta ve yalnızca yerel kullanımda istek yapılmaz. Sorgu başlattığınızda yalnızca o kaynağın ihtiyaç duyduğu hedef değeri ve cihazınızın IP adresi seçtiğiniz sağlayıcıya gider. Her sorgudan önce neyin hangi adrese gideceği gösterilir.
- **Ping:** aktif bir kontroldür; paketler doğrudan hedefe gider. Varsayılan olarak seçili değildir.
- **Harita:** harita ekrandayken o bölgenin harita karoları tile.openstreetmap.org adresinden yüklenir. "Yaklaşık adresi bul" koordinatları nominatim.openstreetmap.org adresine gönderir.
- **Toplanmayanlar:** rehber, SMS, konum, mikrofon, dosyalar, pano, tarama geçmişi, analitik, reklam kimliği.
- **Dışa aktarma:** dosyalar yalnızca seçtiğiniz yere kaydedilir veya paylaştığınız uygulamaya gider ve API anahtarı içermez. Araştırmayı silmek, dışa aktarılmış dosyaları silmez.
- IP konumu bir ağın tahmini konumudur; bir kişinin veya cihazın konumu değildir. Bilginin kamuya açık olması, kişisel verilerin sınırsız yeniden yayımlanmasına izin vermez.
