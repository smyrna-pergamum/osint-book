'use strict';
const languages={en:'English'};
const language='en';
document.documentElement.lang=language;
const dictionary={
  "Domain": {
    "tr": "Alan adı",
    "de": "Domain"
  },
  "IP address": {
    "tr": "IP adresi",
    "de": "IP-Adresse"
  },
  "Organization": {
    "tr": "Kuruluş",
    "de": "Organisation"
  },
  "Package": {
    "tr": "Paket",
    "de": "Paket"
  },
  "File hash": {
    "tr": "Dosya özeti",
    "de": "Datei-Hash"
  },
  "Unreviewed": {
    "tr": "İncelenmedi",
    "de": "Ungeprüft"
  },
  "Verified": {
    "tr": "Doğrulandı",
    "de": "Bestätigt"
  },
  "Conflicting": {
    "tr": "Çelişkili",
    "de": "Widersprüchlich"
  },
  "Pending": {
    "tr": "Bekliyor",
    "de": "Wartend"
  },
  "Running": {
    "tr": "Çalışıyor",
    "de": "Läuft"
  },
  "Completed": {
    "tr": "Tamamlandı",
    "de": "Abgeschlossen"
  },
  "No results": {
    "tr": "Sonuç yok",
    "de": "Keine Ergebnisse"
  },
  "Rate limited": {
    "tr": "Kota sınırı",
    "de": "Anfragelimit"
  },
  "Key rejected": {
    "tr": "Anahtar reddedildi",
    "de": "Schlüssel abgelehnt"
  },
  "Missing key": {
    "tr": "Anahtar eksik",
    "de": "Schlüssel fehlt"
  },
  "Network error": {
    "tr": "Ağ hatası",
    "de": "Netzwerkfehler"
  },
  "Timeout": {
    "tr": "Zaman aşımı",
    "de": "Zeitüberschreitung"
  },
  "Service error": {
    "tr": "Servis hatası",
    "de": "Dienstfehler"
  },
  "Invalid response": {
    "tr": "Yanıt okunamadı",
    "de": "Ungültige Antwort"
  },
  "Response too large": {
    "tr": "Yanıt çok büyük",
    "de": "Antwort zu groß"
  },
  "Cancelled": {
    "tr": "İptal edildi",
    "de": "Abgebrochen"
  },
  "Unsupported": {
    "tr": "Desteklenmiyor",
    "de": "Nicht unterstützt"
  },
  "General": {
    "tr": "Genel",
    "de": "Allgemein"
  },
  "No source link": {
    "tr": "Kaynak bağlantısı yok",
    "de": "Kein Quellenlink"
  },
  "Operation failed.": {
    "tr": "İşlem tamamlanamadı.",
    "de": "Vorgang fehlgeschlagen."
  },
  "Invalid server response.": {
    "tr": "Sunucudan geçerli yanıt alınamadı.",
    "de": "Ungültige Serverantwort."
  },
  "Password": {
    "tr": "Erişim parolası",
    "de": "Passwort"
  },
  "Sign in →": {
    "tr": "Çalışma alanına gir →",
    "de": "Anmelden →"
  },
  "Open-source intelligence": {
    "tr": "Açık kaynak istihbaratı",
    "de": "Open-Source-Intelligence"
  },
  "Collect findings. Discover connections.": {
    "tr": "Bulguları topla. Bağlantıları gör.",
    "de": "Befunde sammeln. Zusammenhänge erkennen."
  },
  "Traceable evidence and a shared investigation workspace.": {
    "tr": "Kaynağı belli bilgiler, izlenebilir araştırmalar ve tek bir ortak çalışma alanı.",
    "de": "Nachvollziehbare Belege und ein gemeinsamer Recherchebereich."
  },
  "LOCAL WORKSPACE": {
    "tr": "YEREL ÇALIŞMA ALANI",
    "de": "LOKALER ARBEITSBEREICH"
  },
  "Continue your research": {
    "tr": "Araştırmana devam et",
    "de": "Recherche fortsetzen"
  },
  "Sign in to your Windows server.": {
    "tr": "Windows sunucundaki araştırmalara parolalı oturumla eriş.",
    "de": "Am Windows-Server anmelden."
  },
  "Research workspace": {
    "tr": "Araştırma çalışma alanı",
    "de": "Recherchebereich"
  },
  "▦  All investigations": {
    "tr": "▦  Tüm araştırmalar",
    "de": "▦  Alle Recherchen"
  },
  "+  New investigation": {
    "tr": "+  Yeni araştırma",
    "de": "+  Neue Recherche"
  },
  "RECENT INVESTIGATIONS": {
    "tr": "SON ARAŞTIRMALAR",
    "de": "LETZTE RECHERCHEN"
  },
  "⚙  Settings": {
    "tr": "⚙  Kaynaklar ve erişim",
    "de": "⚙  Einstellungen"
  },
  "↗  Sign out": {
    "tr": "↗  Oturumu kapat",
    "de": "↗  Abmelden"
  },
  " Windows server": {
    "tr": " Windows sunucusu",
    "de": " Windows-Server"
  },
  "Data is stored on the server.": {
    "tr": "Veriler sunucuda saklanır.",
    "de": "Daten werden auf dem Server gespeichert."
  },
  "Workspace": {
    "tr": "Çalışma alanı",
    "de": "Arbeitsbereich"
  },
  "Investigation": {
    "tr": "Araştırma",
    "de": "Recherche"
  },
  "Overview": {
    "tr": "Genel bakış",
    "de": "Übersicht"
  },
  "Refresh": {
    "tr": "Yenile",
    "de": "Aktualisieren"
  },
  "●  Shared local database": {
    "tr": "●  Ortak yerel veritabanı",
    "de": "●  Gemeinsame lokale Datenbank"
  },
  "Search investigations…": {
    "tr": "Araştırmalarda ara…",
    "de": "Recherchen durchsuchen…"
  },
  "Your research, together.": {
    "tr": "Araştırmaların, bir arada.",
    "de": "Recherchen an einem Ort."
  },
  "Collect public information, review findings and report with sources.": {
    "tr": "Açık kaynaklardan bilgi topla, bulguları incele ve kaynaklarıyla raporla.",
    "de": "Öffentliche Informationen sammeln, Befunde prüfen und mit Quellen berichten."
  },
  "Import JSON": {
    "tr": "JSON içe aktar",
    "de": "JSON importieren"
  },
  "+ New investigation": {
    "tr": "+ Yeni araştırma",
    "de": "+ Neue Recherche"
  },
  "Active investigations": {
    "tr": "Aktif araştırma",
    "de": "Aktive Recherchen"
  },
  "In the shared workspace": {
    "tr": "Ortak çalışma alanında",
    "de": "Im gemeinsamen Arbeitsbereich"
  },
  "Research targets": {
    "tr": "Araştırılan hedef",
    "de": "Rechercheziele"
  },
  "Domains, IPs and more": {
    "tr": "Alan adı, IP ve daha fazlası",
    "de": "Domains, IPs und mehr"
  },
  "Saved findings": {
    "tr": "Kayıtlı bulgu",
    "de": "Gespeicherte Befunde"
  },
  "With source and retrieval time": {
    "tr": "Kaynak ve zaman bilgisiyle",
    "de": "Mit Quelle und Abrufzeit"
  },
  "Archive": {
    "tr": "Arşiv",
    "de": "Archiv"
  },
  "Investigation files": {
    "tr": "Araştırma dosyaları",
    "de": "Rechercheakten"
  },
  "Show active": {
    "tr": "Aktifleri göster",
    "de": "Aktive anzeigen"
  },
  "Show archive": {
    "tr": "Arşivi göster",
    "de": "Archiv anzeigen"
  },
  "Active": {
    "tr": "Aktif",
    "de": "Aktiv"
  },
  "Add targets and choose sources to begin.": {
    "tr": "Hedef ekle, kaynaklarını seç ve araştırmaya başla.",
    "de": "Ziele hinzufügen und Quellen auswählen."
  },
  "Open →": {
    "tr": "Aç →",
    "de": "Öffnen →"
  },
  "No investigations yet": {
    "tr": "Henüz araştırma yok",
    "de": "Noch keine Recherchen"
  },
  "Create an investigation or import an Android JSON export.": {
    "tr": "Yeni bir araştırma oluştur veya Android uygulamasından dışa aktardığın JSON dosyasını içe aktar.",
    "de": "Recherche erstellen oder Android-JSON-Export importieren."
  },
  "Automatic analysis does not replace manual verification.": {
    "tr": "Kaynak çıktıları kanıt olarak saklanır. Otomatik analizler, manuel doğrulamanın yerini almaz.",
    "de": "Automatische Analyse ersetzt keine manuelle Prüfung."
  },
  "Investigation name": {
    "tr": "Araştırma adı",
    "de": "Name der Recherche"
  },
  "Example: example.org infrastructure": {
    "tr": "Örn. example.org altyapı incelemesi",
    "de": "Beispiel: example.org-Infrastruktur"
  },
  "Description": {
    "tr": "Açıklama",
    "de": "Beschreibung"
  },
  "Purpose, scope and research notes": {
    "tr": "Amaç, kapsam ve araştırma notları",
    "de": "Zweck, Umfang und Notizen"
  },
  "Create investigation": {
    "tr": "Araştırmayı oluştur",
    "de": "Recherche erstellen"
  },
  "New investigation": {
    "tr": "Yeni araştırma",
    "de": "Neue Recherche"
  },
  "Android and web JSON files are supported. Imported records receive new IDs. Limit: 20 MiB.": {
    "tr": "Android ve web araştırma dosyaları desteklenir. Kayıtlar yeni kimliklerle içe aktarılır; mevcut araştırmalar değiştirilmez. Dosya sınırı: 20 MiB.",
    "de": "Android- und Web-JSON-Dateien werden unterstützt. Importierte Datensätze erhalten neue IDs. Grenze: 20 MiB."
  },
  "Import": {
    "tr": "İçe aktar",
    "de": "Importieren"
  },
  "Import investigation": {
    "tr": "Araştırma içe aktar",
    "de": "Recherche importieren"
  },
  "Choose a file.": {
    "tr": "Dosya seçin.",
    "de": "Datei auswählen."
  },
  "File exceeds 20 MiB.": {
    "tr": "Dosya 20 MiB sınırını aşıyor.",
    "de": "Datei überschreitet 20 MiB."
  },
  "Investigation imported.": {
    "tr": "Araştırma içe aktarıldı.",
    "de": "Recherche importiert."
  },
  "Analysis": {
    "tr": "Analiz",
    "de": "Analyse"
  },
  "Notes & relations": {
    "tr": "Notlar & ilişkiler",
    "de": "Notizen & Beziehungen"
  },
  "Report": {
    "tr": "Rapor",
    "de": "Bericht"
  },
  "Choose sources to extend your research.": {
    "tr": "Kaynaklarını seçerek araştırmanı genişlet.",
    "de": "Quellen auswählen, um die Recherche zu erweitern."
  },
  "Archived investigation": {
    "tr": "Arşivlenmiş araştırma",
    "de": "Archivierte Recherche"
  },
  "Edit": {
    "tr": "Düzenle",
    "de": "Bearbeiten"
  },
  "Export": {
    "tr": "Dışa aktar",
    "de": "Exportieren"
  },
  "Investigation sections": {
    "tr": "Araştırma bölümleri",
    "de": "Recherchebereiche"
  },
  "Title": {
    "tr": "Başlık",
    "de": "Titel"
  },
  "Save": {
    "tr": "Kaydet",
    "de": "Speichern"
  },
  "Edit investigation": {
    "tr": "Araştırmayı düzenle",
    "de": "Recherche bearbeiten"
  },
  "Unarchive": {
    "tr": "Arşivden çıkar",
    "de": "Aus Archiv holen"
  },
  "Archive investigation": {
    "tr": "Arşivle",
    "de": "Recherche archivieren"
  },
  "Delete": {
    "tr": "Sil",
    "de": "Löschen"
  },
  "Delete this investigation?": {
    "tr": "Araştırma silinsin mi?",
    "de": "Diese Recherche löschen?"
  },
  "Cancel": {
    "tr": "Vazgeç",
    "de": "Abbrechen"
  },
  "Delete investigation": {
    "tr": "Araştırmayı sil",
    "de": "Recherche löschen"
  },
  "Target type": {
    "tr": "Hedef türü",
    "de": "Zieltyp"
  },
  "Detect automatically": {
    "tr": "Otomatik algıla",
    "de": "Automatisch erkennen"
  },
  "Target": {
    "tr": "Hedef",
    "de": "Ziel"
  },
  "+ Add target": {
    "tr": "+ Hedef ekle",
    "de": "+ Ziel hinzufügen"
  },
  "Target warnings: ": {
    "tr": "Hedef uyarıları: ",
    "de": "Zielhinweise: "
  },
  "Secret URL parameters removed": {
    "tr": "Gizli URL parametreleri temizlendi",
    "de": "Geheime URL-Parameter entfernt"
  },
  "URL user information removed": {
    "tr": "URL kullanıcı bilgisi temizlendi",
    "de": "URL-Benutzerdaten entfernt"
  },
  "Private IP address": {
    "tr": "Özel IP adresi",
    "de": "Private IP-Adresse"
  },
  "No package version specified": {
    "tr": "Paket sürümü belirtilmedi",
    "de": "Keine Paketversion angegeben"
  },
  "Investigation targets": {
    "tr": "Araştırma hedefleri",
    "de": "Rechercheziele"
  },
  "Add a target, then review the selected sources before querying.": {
    "tr": "Bir hedef ekle. Her sorgudan önce gönderilecek değeri ve alıcı kaynakları gözden geçir.",
    "de": "Ziel hinzufügen und vor der Abfrage die ausgewählten Quellen prüfen."
  },
  "Cancel operation": {
    "tr": "İptal et",
    "de": "Vorgang abbrechen"
  },
  "Query sources →": {
    "tr": "Kaynakları sorgula →",
    "de": "Quellen abfragen →"
  },
  "Add your first target": {
    "tr": "İlk hedefini ekle",
    "de": "Erstes Ziel hinzufügen"
  },
  "Start with a domain, URL, IP, organization, repository, package or hash.": {
    "tr": "Alan adı, URL, IP, kuruluş, GitHub deposu, paket veya dosya özetiyle başlayabilirsin.",
    "de": "Mit Domain, URL, IP, Organisation, Repository, Paket oder Hash beginnen."
  },
  "API key missing on server": {
    "tr": "Sunucuda API anahtarı eksik",
    "de": "API-Schlüssel fehlt auf dem Server"
  },
  "Key configured": {
    "tr": "Anahtar hazır",
    "de": "Schlüssel konfiguriert"
  },
  "No key required": {
    "tr": "Anahtarsız erişim",
    "de": "Kein Schlüssel erforderlich"
  },
  "Target to send: ": {
    "tr": "Gönderilecek hedef: ",
    "de": "Gesendetes Ziel: "
  },
  "Selected services receive this target and the server public IP. Some URL providers only receive the domain.": {
    "tr": "Seçtiğin servisler bu hedefi ve sunucunun dış IP adresini görecek. URL hedeflerinde bazı kaynaklar yalnızca alan adını kullanır.",
    "de": "Ausgewählte Dienste erhalten das Ziel und die öffentliche Server-IP. Einige URL-Anbieter erhalten nur die Domain."
  },
  "Query selected sources": {
    "tr": "Seçili kaynakları sorgula",
    "de": "Ausgewählte Quellen abfragen"
  },
  "Choose sources": {
    "tr": "Kaynak seçimi",
    "de": "Quellen auswählen"
  },
  "Select at least one source.": {
    "tr": "En az bir kaynak seçin.",
    "de": "Mindestens eine Quelle auswählen."
  },
  "No matching findings": {
    "tr": "Gösterilecek bulgu yok",
    "de": "Keine passenden Befunde"
  },
  "Run a query or change your filters.": {
    "tr": "Kaynak sorgusu çalıştır veya filtrelerini değiştir.",
    "de": "Abfrage starten oder Filter ändern."
  },
  "Search findings, sources and tags…": {
    "tr": "Bulgularda, kaynaklarda ve etiketlerde ara…",
    "de": "Befunde, Quellen und Tags durchsuchen…"
  },
  "All review states": {
    "tr": "Tüm durumlar",
    "de": "Alle Prüfstatus"
  },
  "★ Favorites": {
    "tr": "★ Favoriler",
    "de": "★ Favoriten"
  },
  "View source ↗": {
    "tr": "Kaynağa git ↗",
    "de": "Quelle ansehen ↗"
  },
  "Published: ": {
    "tr": "Yayın: ",
    "de": "Veröffentlicht: "
  },
  "Review": {
    "tr": "İncele",
    "de": "Prüfen"
  },
  "Finding / Value": {
    "tr": "Bulgu / Değer",
    "de": "Befund / Wert"
  },
  "Source / Target": {
    "tr": "Kaynak / Hedef",
    "de": "Quelle / Ziel"
  },
  "Retrieved at": {
    "tr": "Alınma zamanı",
    "de": "Abgerufen am"
  },
  "Review state": {
    "tr": "İnceleme",
    "de": "Prüfstatus"
  },
  "Open source ↗": {
    "tr": "Kaynağı aç ↗",
    "de": "Quelle öffnen ↗"
  },
  "Review status": {
    "tr": "İnceleme durumu",
    "de": "Prüfstatus"
  },
  "Favorite": {
    "tr": "Favori",
    "de": "Favorit"
  },
  "Tags (comma-separated)": {
    "tr": "Etiketler (virgülle ayır)",
    "de": "Tags (durch Kommas getrennt)"
  },
  "Save changes": {
    "tr": "Değişiklikleri kaydet",
    "de": "Änderungen speichern"
  },
  "Evidence record": {
    "tr": "Kanıt kaydı",
    "de": "Belegdatensatz"
  },
  "No source excerpt.": {
    "tr": "Kaynak alıntısı yok.",
    "de": "Kein Quellenauszug."
  },
  "The checksum detects changes to the local evidence; it does not prove truth.": {
    "tr": "Özet değeri yerel kanıtın değişimini kontrol etmek içindir; içeriğin doğruluğunu kanıtlamaz.",
    "de": "Die Prüfsumme erkennt lokale Änderungen, bestätigt aber nicht die Wahrheit."
  },
  "Add a note to this finding": {
    "tr": "Bu bulguya not ekle",
    "de": "Notiz zu diesem Befund hinzufügen"
  },
  "Findings analysis": {
    "tr": "Bulguların analizi",
    "de": "Befundanalyse"
  },
  "Rule-based source analysis. This is not AI commentary and does not produce an overall risk score.": {
    "tr": "Mevcut kural motorunun kaynaklara dayalı değerlendirmesi. Bu bölüm AI yorumu değildir; toplam risk puanı üretilmez.",
    "de": "Regelbasierte Quellenanalyse, kein KI-Kommentar und kein Gesamtrisikowert."
  },
  "Warning": {
    "tr": "Dikkat",
    "de": "Warnung"
  },
  "Missing information": {
    "tr": "Eksik bilgi",
    "de": "Fehlende Informationen"
  },
  "Information": {
    "tr": "Bilgi",
    "de": "Information"
  },
  "Evidence: ": {
    "tr": "Kanıt: ",
    "de": "Beleg: "
  },
  "Waiting for analysis data": {
    "tr": "Analiz için veri bekleniyor",
    "de": "Warte auf Analysedaten"
  },
  "Add a target and query sources.": {
    "tr": "Hedef ekleyip kaynak sorgusu çalıştır.",
    "de": "Ziel hinzufügen und Quellen abfragen."
  },
  "Timeline": {
    "tr": "Zaman çizelgesi",
    "de": "Zeitachse"
  },
  "AI interpretation · Phase two": {
    "tr": "AI yorumlayıcı · İkinci aşama",
    "de": "KI-Auswertung · Zweite Phase"
  },
  "AI is not enabled yet. Source findings and rule analysis remain separate.": {
    "tr": "AI bağlantısı henüz etkin değil. Bu sürüm kaynak bulgularını ve kural analizini sonraki entegrasyon için ayrı tutar.",
    "de": "KI ist noch nicht aktiviert. Quellenbefunde und Regelanalyse bleiben getrennt."
  },
  "Research notes": {
    "tr": "Araştırma notları",
    "de": "Recherchenotizen"
  },
  "+ Add note": {
    "tr": "+ Not ekle",
    "de": "+ Notiz hinzufügen"
  },
  " · Finding: ": {
    "tr": " · Bulgu: ",
    "de": " · Befund: "
  },
  "Keep context, conclusions and next steps here.": {
    "tr": "Araştırma bağlamını, çıkarımlarını ve takip edilecek adımları burada tut.",
    "de": "Kontext, Schlussfolgerungen und nächste Schritte festhalten."
  },
  "Relations": {
    "tr": "İlişkiler",
    "de": "Beziehungen"
  },
  "+ Add relation": {
    "tr": "+ İlişki ekle",
    "de": "+ Beziehung hinzufügen"
  },
  "User inference": {
    "tr": "Kullanıcı çıkarımı",
    "de": "Benutzerschlussfolgerung"
  },
  "Source stated": {
    "tr": "Kaynakta belirtilmiş",
    "de": "Von Quelle angegeben"
  },
  "Record links between targets and findings.": {
    "tr": "Hedefler ve bulgular arasındaki bağlantıları kaydet.",
    "de": "Verbindungen zwischen Zielen und Befunden erfassen."
  },
  "Note": {
    "tr": "Not",
    "de": "Notiz"
  },
  "Save note": {
    "tr": "Notu kaydet",
    "de": "Notiz speichern"
  },
  "Finding note": {
    "tr": "Bulgu notu",
    "de": "Befundnotiz"
  },
  "Research note": {
    "tr": "Araştırma notu",
    "de": "Recherchenotiz"
  },
  "A relation needs at least two targets or findings.": {
    "tr": "İlişki için en az iki hedef veya bulgu gerekli.",
    "de": "Eine Beziehung benötigt mindestens zwei Ziele oder Befunde."
  },
  "From": {
    "tr": "Başlangıç",
    "de": "Von"
  },
  "Relation type": {
    "tr": "İlişki türü",
    "de": "Beziehungstyp"
  },
  "Example: related infrastructure": {
    "tr": "Örn. ilişkili altyapı",
    "de": "Beispiel: zugehörige Infrastruktur"
  },
  "To": {
    "tr": "Bitiş",
    "de": "Zu"
  },
  "This relation will be marked as a user inference.": {
    "tr": "Eklenen ilişki kullanıcı çıkarımı olarak işaretlenir.",
    "de": "Diese Beziehung wird als Benutzerschlussfolgerung markiert."
  },
  "Save relation": {
    "tr": "İlişkiyi kaydet",
    "de": "Beziehung speichern"
  },
  "Add relation": {
    "tr": "İlişki ekle",
    "de": "Beziehung hinzufügen"
  },
  "Reports and export": {
    "tr": "Rapor ve veri aktarımı",
    "de": "Berichte und Export"
  },
  "JSON carries the full investigation. CSV exports findings. Use the browser Print dialog to save PDF.": {
    "tr": "JSON dosyası tüm araştırmayı taşır. CSV bulguları tablo olarak dışa aktarır. PDF için rapor ekranında tarayıcının “PDF olarak kaydet” seçeneğini kullan.",
    "de": "JSON enthält die vollständige Recherche. CSV exportiert Befunde. PDF über den Druckdialog speichern."
  },
  "Download JSON": {
    "tr": "JSON indir",
    "de": "JSON herunterladen"
  },
  "Download CSV": {
    "tr": "CSV indir",
    "de": "CSV herunterladen"
  },
  "PDF / Report": {
    "tr": "PDF / Rapor",
    "de": "PDF / Bericht"
  },
  "Print / Save PDF": {
    "tr": "Yazdır / PDF olarak kaydet",
    "de": "Drucken / PDF speichern"
  },
  "Investigation report": {
    "tr": "Araştırma raporu",
    "de": "Recherchebericht"
  },
  "Scope and method": {
    "tr": "Kapsam ve yöntem",
    "de": "Umfang und Methode"
  },
  "Queries were initiated by the user. Provider verdicts belong to their sources; no result does not imply safety.": {
    "tr": "Seçilen kaynaklara kullanıcı tarafından başlatılan sorgular uygulanmıştır. Kaynak sonuçları kendi sağlayıcılarına aittir. Sonuç bulunamaması güvenli olduğu anlamına gelmez.",
    "de": "Abfragen wurden vom Benutzer gestartet. Bewertungen stammen von den jeweiligen Anbietern; kein Ergebnis bedeutet nicht Sicherheit."
  },
  "Source queries": {
    "tr": "Kaynak sorguları",
    "de": "Quellenabfragen"
  },
  "Findings": {
    "tr": "Bulgular",
    "de": "Befunde"
  },
  "Notes": {
    "tr": "Notlar",
    "de": "Notizen"
  },
  "Inference": {
    "tr": "Çıkarım",
    "de": "Schlussfolgerung"
  },
  "API keys": {
    "tr": "API anahtarları",
    "de": "API-Schlüssel"
  },
  "Key required": {
    "tr": "Anahtar gerekli",
    "de": "Schlüssel erforderlich"
  },
  "Without key": {
    "tr": "Anahtarsız",
    "de": "Ohne Schlüssel"
  },
  "Documentation ↗": {
    "tr": "Belgeler ↗",
    "de": "Dokumentation ↗"
  },
  "Username": {
    "tr": "Kullanıcı adı",
    "de": "Benutzername"
  },
  "Language": {
    "tr": "Dil",
    "de": "Sprache"
  },
  "Administrator": {
    "tr": "Yönetici",
    "de": "Administrator"
  },
  "User": {
    "tr": "Kullanıcı",
    "de": "Benutzer"
  },
  "Users": {
    "tr": "Kullanıcılar",
    "de": "Benutzer"
  },
  "Add user": {
    "tr": "Kullanıcı ekle",
    "de": "Benutzer hinzufügen"
  },
  "Enabled": {
    "tr": "Etkin",
    "de": "Aktiviert"
  },
  "Disabled": {
    "tr": "Devre dışı",
    "de": "Deaktiviert"
  },
  "Enable": {
    "tr": "Etkinleştir",
    "de": "Aktivieren"
  },
  "Disable": {
    "tr": "Devre dışı bırak",
    "de": "Deaktivieren"
  },
  "Reset password": {
    "tr": "Parolayı değiştir",
    "de": "Passwort ändern"
  },
  "New password": {
    "tr": "Yeni parola",
    "de": "Neues Passwort"
  },
  "At least 12 characters": {
    "tr": "En az 12 karakter",
    "de": "Mindestens 12 Zeichen"
  },
  "Manage users": {
    "tr": "Kullanıcıları yönet",
    "de": "Benutzer verwalten"
  },
  "Manage API keys": {
    "tr": "API anahtarlarını yönet",
    "de": "API-Schlüssel verwalten"
  },
  "Settings": {
    "tr": "Ayarlar",
    "de": "Einstellungen"
  },
  "Only administrators can manage users and API keys.": {
    "tr": "Kullanıcı ve anahtar yönetimi yalnızca yöneticilere açıktır.",
    "de": "Nur Administratoren können Benutzer und API-Schlüssel verwalten."
  },
  "All users share this server workspace.": {
    "tr": "Tüm kullanıcılar bu sunucudaki çalışma alanını paylaşır.",
    "de": "Alle Benutzer teilen diesen Server-Arbeitsbereich."
  },
  "Your server administrator creates your account.": {
    "tr": "Hesabınızı sunucu yöneticisi oluşturur.",
    "de": "Ihr Serveradministrator erstellt Ihr Konto."
  },
  "Saved.": {
    "tr": "Kaydedildi.",
    "de": "Gespeichert."
  },
  "API key": {
    "tr": "API anahtarı",
    "de": "API-Schlüssel"
  },
  "Replace key": {
    "tr": "Anahtarı değiştir",
    "de": "Schlüssel ersetzen"
  },
  "Add key": {
    "tr": "Anahtar ekle",
    "de": "Schlüssel hinzufügen"
  },
  "Delete stored key": {
    "tr": "Kayıtlı anahtarı sil",
    "de": "Gespeicherten Schlüssel löschen"
  },
  "Keys are encrypted on the server and never returned to the browser. An environment key is used if no stored key exists.": {
    "tr": "Anahtarlar sunucuda şifrelenir ve tarayıcıya geri gönderilmez. Kayıtlı anahtar yoksa ortam değişkeni kullanılır.",
    "de": "Schlüssel werden auf dem Server verschlüsselt und nie an den Browser zurückgegeben. Ohne gespeicherten Schlüssel wird die Umgebungsvariable verwendet."
  },
  "Cancel all queries": {
    "tr": "Tüm sorguları iptal et",
    "de": "Alle Abfragen abbrechen"
  },
  "Requests": {
    "tr": "İstekler",
    "de": "Anfragen"
  },
  "Cancel request": {
    "tr": "İsteği iptal et",
    "de": "Anfrage abbrechen"
  },
  "Request cancelled. Completed saves are not undone; refresh to check.": {
    "tr": "İstek iptal edildi. Tamamlanmış kayıtlar geri alınmaz; kontrol etmek için yenileyin.",
    "de": "Anfrage abgebrochen. Abgeschlossene Speichervorgänge bleiben erhalten; zur Prüfung aktualisieren."
  },
  "Targets": {
    "tr": "Hedefler",
    "de": "Ziele"
  },
  "Map": {
    "tr": "Harita",
    "de": "Karte"
  },
  "Load map": {
    "tr": "Haritayı yükle",
    "de": "Karte laden"
  },
  "Stop map": {
    "tr": "Haritayı durdur",
    "de": "Karte stoppen"
  },
  "No coordinates yet. Query an IP geolocation source first.": {
    "tr": "Henüz koordinat yok. Önce bir IP konum kaynağını sorgulayın.",
    "de": "Noch keine Koordinaten. Zuerst eine IP-Geolokalisierungsquelle abfragen."
  },
  "IP locations are approximate network locations, not a person or device location.": {
    "tr": "IP konumları yaklaşık ağ konumlarıdır; kişi veya cihaz konumu değildir.",
    "de": "IP-Standorte sind ungefähre Netzwerkstandorte, keine Personen- oder Gerätestandorte."
  },
  "Loading the map sends these coordinates and your browser IP to OpenStreetMap.": {
    "tr": "Haritayı yüklemek bu koordinatları ve tarayıcınızın IP adresini OpenStreetMap ile paylaşır.",
    "de": "Beim Laden werden diese Koordinaten und Ihre Browser-IP an OpenStreetMap gesendet."
  },
  "Coordinates": {
    "tr": "Koordinatlar",
    "de": "Koordinaten"
  },
  "Active check: ping sends four packets directly from the server to the target.": {
    "tr": "Aktif kontrol: ping sunucudan hedefe doğrudan dört paket gönderir.",
    "de": "Aktive Prüfung: Ping sendet vier Pakete direkt vom Server an das Ziel."
  },
  "I authorize the selected active ping check.": {
    "tr": "Seçtiğim aktif ping kontrolüne izin veriyorum.",
    "de": "Ich bestätige die ausgewählte aktive Ping-Prüfung."
  },
  "Ping results contain the Windows output. Inspect it for reachability.": {
    "tr": "Ping sonuçları Windows çıktısını içerir. Erişilebilirlik için çıktıyı inceleyin.",
    "de": "Ping-Ergebnisse enthalten die Windows-Ausgabe. Erreichbarkeit anhand der Ausgabe prüfen."
  },
  "Confirm the active ping check.": {
    "tr": "Aktif ping kontrolünü onaylayın.",
    "de": "Aktive Ping-Prüfung bestätigen."
  },
  "This permanently deletes the investigation and its records.": {
    "tr": "Araştırma ve kayıtları kalıcı olarak silinir.",
    "de": "Die Recherche und ihre Datensätze werden dauerhaft gelöscht."
  },
  "Generated": {
    "tr": "Hazırlanma",
    "de": "Erstellt"
  },
  "All targets and findings included.": {
    "tr": "Tüm hedefler ve bulgular dahildir.",
    "de": "Alle Ziele und Befunde enthalten."
  },
  "days": {
    "tr": "gün",
    "de": "Tage"
  },
  "findings": {
    "tr": "bulgu",
    "de": "Befunde"
  },
  "targets": {
    "tr": "hedef",
    "de": "Ziele"
  },
  "requests": {
    "tr": "istek",
    "de": "Anfragen"
  },
  "login_required": {
    "en": "Sign in required.",
    "tr": "Giriş gerekli.",
    "de": "Anmeldung erforderlich."
  },
  "invalid_credentials": {
    "en": "Incorrect username or password.",
    "tr": "Kullanıcı adı veya parola yanlış.",
    "de": "Benutzername oder Passwort falsch."
  },
  "admin_required": {
    "en": "Administrator access required.",
    "tr": "Yönetici yetkisi gerekli.",
    "de": "Administratorrechte erforderlich."
  },
  "password_length": {
    "en": "Password must contain 12–256 characters.",
    "tr": "Parola 12–256 karakter olmalı.",
    "de": "Passwort muss 12–256 Zeichen enthalten."
  },
  "username_invalid": {
    "en": "Username: 3–40 letters, numbers, dots, hyphens or underscores.",
    "tr": "Kullanıcı adı: 3–40 harf, rakam, nokta, tire veya alt çizgi.",
    "de": "Benutzername: 3–40 Buchstaben, Zahlen, Punkte, Bindestriche oder Unterstriche."
  },
  "username_exists": {
    "en": "Username already exists.",
    "tr": "Kullanıcı adı zaten var.",
    "de": "Benutzername existiert bereits."
  },
  "last_admin": {
    "en": "The last enabled administrator cannot be disabled or demoted.",
    "tr": "Son etkin yönetici devre dışı bırakılamaz.",
    "de": "Der letzte aktive Administrator kann nicht deaktiviert oder herabgestuft werden."
  },
  "login_rate_limited": {
    "en": "Too many login attempts. Wait one minute.",
    "tr": "Çok fazla giriş denemesi. Bir dakika bekleyin.",
    "de": "Zu viele Anmeldeversuche. Eine Minute warten."
  },
  "active_confirmation_required": {
    "en": "Confirm the active ping check.",
    "tr": "Aktif ping kontrolünü onaylayın.",
    "de": "Aktive Ping-Prüfung bestätigen."
  },
  "invalid_target": {
    "en": "Invalid target.",
    "tr": "Geçersiz hedef.",
    "de": "Ungültiges Ziel."
  },
  "select_sources": {
    "en": "Select suitable sources.",
    "tr": "Uygun kaynak seçin.",
    "de": "Geeignete Quellen auswählen."
  },
  "archived": {
    "en": "Investigation is archived.",
    "tr": "Araştırma arşivde.",
    "de": "Recherche ist archiviert."
  },
  "not_found": {
    "en": "Record not found.",
    "tr": "Kayıt bulunamadı.",
    "de": "Datensatz nicht gefunden."
  },
  "operation_failed": {
    "en": "Operation failed.",
    "tr": "İşlem tamamlanamadı.",
    "de": "Vorgang fehlgeschlagen."
  },
  "import_failed": {
    "en": "Invalid investigation file.",
    "tr": "Geçersiz araştırma dosyası.",
    "de": "Ungültige Recherchedatei."
  },
  "request_too_large": {
    "en": "Request exceeds the size limit.",
    "tr": "İstek boyut sınırını aşıyor.",
    "de": "Anfrage überschreitet das Größenlimit."
  },
  "field_too_long": {
    "en": "Field is too long.",
    "tr": "Alan çok uzun.",
    "de": "Feld ist zu lang."
  },
  "invalid_request": {
    "en": "Invalid request.",
    "tr": "Geçersiz istek.",
    "de": "Ungültige Anfrage."
  },
  "note_required": {
    "en": "Enter a note.",
    "tr": "Not girin.",
    "de": "Notiz eingeben."
  },
  "title_required": {
    "en": "Enter a title.",
    "tr": "Başlık girin.",
    "de": "Titel eingeben."
  },
  "target_exists": {
    "en": "Target already exists.",
    "tr": "Hedef zaten var.",
    "de": "Ziel existiert bereits."
  },
  "key_invalid": {
    "en": "Enter a valid API key.",
    "tr": "Geçerli API anahtarı girin.",
    "de": "Gültigen API-Schlüssel eingeben."
  },
  "invalid_provider": {
    "en": "Invalid provider.",
    "tr": "Geçersiz kaynak.",
    "de": "Ungültiger Anbieter."
  },
  "query_running": {
    "en": "A query is already running for this target.",
    "tr": "Bu hedef için sorgu zaten çalışıyor.",
    "de": "Für dieses Ziel läuft bereits eine Abfrage."
  },
  "query_queue_full": {
    "en": "Query queue is full.",
    "tr": "Sorgu kuyruğu dolu.",
    "de": "Abfragewarteschlange ist voll."
  },
  "query_wait_delete": {
    "en": "Wait for queries to finish before deleting.",
    "tr": "Silmeden önce sorguların bitmesini bekleyin.",
    "de": "Vor dem Löschen das Ende der Abfragen abwarten."
  },
  "session_limit": {
    "en": "Session limit reached.",
    "tr": "Oturum sınırına ulaşıldı.",
    "de": "Sitzungslimit erreicht."
  }
};
Object.assign(dictionary, {
  'Server management': {tr:'Sunucu yönetimi', de:'Serververwaltung'},
  'Manage accounts on this server. All users share the investigation workspace.': {tr:'Bu sunucudaki hesapları yönetin. Tüm kullanıcılar araştırma alanını paylaşır.', de:'Konten auf diesem Server verwalten. Alle Benutzer teilen den Recherchebereich.'},
  'User accounts': {tr:'Kullanıcı hesapları', de:'Benutzerkonten'},
  'Make administrator': {tr:'Yönetici yap', de:'Zum Administrator machen'},
  'Remove administrator': {tr:'Yönetici yetkisini kaldır', de:'Administratorrechte entfernen'},
  'Delete target': {tr:'Hedefi sil', de:'Ziel löschen'},
  'Delete this target?': {tr:'Bu hedef silinsin mi?', de:'Dieses Ziel löschen?'},
  'This permanently deletes the target, its query history, findings, evidence, linked notes, tags and relations.': {tr:'Hedef, sorgu geçmişi, bulguları, kanıtları, bağlı notları, etiketleri ve ilişkileri kalıcı olarak silinir.', de:'Das Ziel, sein Abfrageverlauf, Befunde, Belege, verknüpfte Notizen, Tags und Beziehungen werden dauerhaft gelöscht.'},
  'Cancel any running queries and wait for them to finish first.': {tr:'Önce çalışan sorguları iptal edin ve bitmelerini bekleyin.', de:'Laufende Abfragen zuerst abbrechen und deren Ende abwarten.'},
  'target_not_found': {en:'Target not found.',tr:'Hedef bulunamadı.',de:'Ziel nicht gefunden.'}
});
Object.assign(dictionary, {
  'Admin panel':{tr:'Yönetici paneli',de:'Administrationsbereich'},
  'Server administration':{tr:'Sunucu yönetimi',de:'Serververwaltung'},
  'Sign in with an administrator account.':{tr:'Yönetici hesabıyla giriş yapın.',de:'Mit einem Administratorkonto anmelden.'},
  'Password reset requests':{tr:'Parola sıfırlama istekleri',de:'Anfragen zur Passwortrücksetzung'},
  'No pending requests.':{tr:'Bekleyen istek yok.',de:'Keine offenen Anfragen.'},
  'Request password reset':{tr:'Parola sıfırlama isteği',de:'Passwort zurücksetzen anfragen'},
  'Send request':{tr:'İsteği gönder',de:'Anfrage senden'},
  'If the account is eligible, the administrator will see your request.':{tr:'Hesap uygunsa yöneticiniz isteğinizi görecek.',de:'Falls das Konto berechtigt ist, sieht die Administration Ihre Anfrage.'},
  'Activity log':{tr:'İşlem kayıtları',de:'Aktivitätsprotokoll'},
  'Latest 500 account and access events.':{tr:'Son 500 hesap ve erişim olayı.',de:'Letzte 500 Konto- und Zugriffsereignisse.'},
  'Time':{tr:'Zaman',de:'Zeit'},'Actor':{tr:'Kullanıcı',de:'Benutzer'},'Action':{tr:'İşlem',de:'Aktion'},'Subject':{tr:'Konu',de:'Betreff'},
  'Administrators manage users and API keys in the separate admin panel.':{tr:'Yöneticiler kullanıcıları ve API anahtarlarını ayrı yönetici panelinden yönetir.',de:'Administratoren verwalten Benutzer und API-Schlüssel im separaten Administrationsbereich.'},
  'reset_rate_limited':{en:'Too many requests; try again later.',tr:'Çok fazla istek; daha sonra tekrar deneyin.',de:'Zu viele Anfragen; versuchen Sie es später erneut.'}
});
Object.assign(dictionary, {
  '+ Add user':{tr:'+ Kullanıcı ekle',de:'+ Benutzer hinzufügen'},
  'All users':{tr:'Tüm kullanıcılar',de:'Alle Benutzer'},
  'accounts':{tr:'hesap',de:'Konten'},
  'Manage access and account permissions.':{tr:'Erişimi ve hesap yetkilerini yönetin.',de:'Zugriff und Kontoberechtigungen verwalten.'},
  'No users found.':{tr:'Kullanıcı bulunamadı.',de:'Keine Benutzer gefunden.'},
  'Allow access to the admin panel.':{tr:'Yönetici paneline erişim sağlar.',de:'Zugriff auf den Administrationsbereich erlauben.'},
  'Create an account for this server.':{tr:'Bu sunucu için bir hesap oluşturun.',de:'Ein Konto für diesen Server erstellen.'},
  'Account details':{tr:'Hesap bilgileri',de:'Kontodaten'},
  'Set a username, password and permission level.':{tr:'Kullanıcı adı, parola ve yetki düzeyini belirleyin.',de:'Benutzernamen, Passwort und Berechtigungsstufe festlegen.'},
  'Review requests before resetting passwords.':{tr:'Parolaları sıfırlamadan önce istekleri inceleyin.',de:'Anfragen vor dem Zurücksetzen von Passwörtern prüfen.'},
  'Manage source API keys on the server.':{tr:'Sunucudaki kaynak API anahtarlarını yönetin.',de:'API-Schlüssel der Quellen auf dem Server verwalten.'},
  'No activity yet.':{tr:'Henüz işlem kaydı yok.',de:'Noch keine Aktivitäten.'},
  'Admin navigation':{tr:'Yönetici menüsü',de:'Administrationsmenü'}
});
Object.assign(dictionary, {
  'Delete user':{tr:'Kullanıcıyı sil',de:'Benutzer löschen'},
  'Delete user?':{tr:'Kullanıcı silinsin mi?',de:'Benutzer löschen?'},
  'Permanently delete this account?':{tr:'Bu hesap kalıcı olarak silinsin mi?',de:'Dieses Konto dauerhaft löschen?'},
  'Active sessions and pending password requests will be removed. Shared investigation data and activity logs remain.':{tr:'Aktif oturumlar ve bekleyen parola sıfırlama istekleri kaldırılır. Paylaşılan araştırma verileri ve işlem kayıtları korunur.',de:'Aktive Sitzungen und offene Passwortanfragen werden entfernt. Gemeinsame Ermittlungsdaten und Aktivitätsprotokolle bleiben erhalten.'},
  'User deleted.':{tr:'Kullanıcı silindi.',de:'Benutzer gelöscht.'},
  'cannot_delete_self':{en:'You cannot delete your own account.',tr:'Kendi hesabınızı silemezsiniz.',de:'Sie können Ihr eigenes Konto nicht löschen.'}
});
function tr(key){return dictionary[key]?.[language] ?? dictionary[key]?.en ?? key;}

document.title='OSINT-Book · '+tr('Research workspace');
