# Doğrulama durumu – neyin gerçekten sınandığı

Bu dosya, hangi desteğin **gerçekten çalıştırılarak sınandığını**, hangisinin yalnızca derlendiğini ve hangisinin hiç
sınanmadığını ayırır. Sınanmayan hiçbir şey "doğrulanmış" olarak listelenmez.

## Nasıl sınandı

1. **Birim testleri (`core/`)** – Minecraft'tan bağımsız çeviri motoru: maskeleme (`%s`, `%d`, `{name}`, `§` renk
   kodları, URL'ler, Patchouli `$(...)` komutları, FTB `{@pagebreak}`), metin analizi, sözlük, önbellek, düzeltmeler,
   sağlayıcı yanıt doğrulama, hız sınırı/yeniden deneme, API anahtarının dosyalara/loglara sızmaması, ücretsiz hizmet
   seçimi (anahtardan), anahtarsız Google istek biçimi, kota/yedek model, kısa metinlerin tek istekte birleşmesi. Ağ
   yerine yerel **sahte sunucular** kullanılır. 140 test; CI'da `./gradlew -p core test`.
2. **Derleme** – her sürüm modülü kendi Gradle/Java sürümüyle CI'da derlenir (1.20.1 için `./gradlew :forge:build`,
   Windows karşılığı `gradlew.bat :forge:build`).
3. **Oyun başlatma testi (duman testi)** – CI'da **gerçek Minecraft istemcisi** başlatılır (Xvfb sanal ekran + Mesa
   yazılımsal OpenGL). Mod içindeki başlangıç denetimi oyunun içinden yerel sahte sağlayıcıya bağlanır (sahte sağlayıcı
   her metne `TR| ` ön eki koyar) ve sonuçları `build/smoke/result.json` dosyasına yazar. Her sürüm iki kez açılır:
   * **yalnız bu mod** – isteğe bağlı modlar yokken oyun açılıyor mu, genel çeviri yolu çalışıyor mu;
   * **FTB Quests + Patchouli ile** (1.12.2'de ayrıca **BetterQuesting**) – özel kancalar gerçek mod sınıflarına
     uygulanıyor mu, metin "mod → çeviri hattı → sağlayıcı → ekran" yolunu baştan sona geçiyor mu, veri (ham metin,
     kimlik, renk/biçim) değişmeden kalıyor mu.

   **"Her modu çevirme" denetimleri** hiçbir mod adını bilmez: Türkçe dosyası olmayan bir dil anahtarının değeri
   (`I18n.get`) ve listede olmayan bir ekrandaki koda gömülü İngilizce yazı kendiliğinden çevrilmelidir.
4. **Gerçek kurulum testi (1.12.2)** – geliştirme ortamı değil: **oyuncunun indireceği jar** (`…-forge.jar`), gerçek bir
   Forge 1.12.2 kurulumunun `mods/` klasörüne konur (portablemc başlatıcısı ile kurulur) ve oyun açılır. CurseForge'daki
   gerçek BetterQuesting, FTB Quests, Patchouli dosyalarıyla ve onlarsız iki kez. İş akışı: `prod-1122.yml`.
5. **Canlı ücretsiz hizmet denetimi** – tipik modpaketi metinleri tüm hattan geçirilip **gerçek** anahtarsız Google
   Çeviri'ye gönderilir ve Türkçe sonuçlar yazdırılır (`live-free.yml`, `LiveFreeServicesTest`).

İş akışları: `.github/workflows/forge-build.yml` (1.20.1 derleme + birim testleri), `smoke.yml` (1.20.1 oyun
başlatma), `versions.yml` (diğer sürümler: derleme + oyun başlatma).

## Sonuçlar

Durum işaretleri: ✅ CI'da geçti · ❌ CI'da başarısız · ⏳ sonuç bekleniyor · — uygulanamaz

| Sürüm | Derleme | Oyun açılışı (yalnız bu mod) | Her mod: dil dosyası | Her mod: ekrandaki sabit yazı | FTB Quests | Patchouli | BetterQuesting |
|---|---|---|---|---|---|---|---|
| 1.20.1 Forge (ana sürüm) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | — |
| 1.19.2 Forge | ✅ | ✅ | ✅ | ✅ | ⏳ | ⏳ | — |
| 1.18.2 Forge | ✅ | ✅ | ✅ | ✅ | ⏳ | ⏳ | — |
| 1.16.5 Forge | ✅ | ✅ | ✅ | ✅ | ⏳ | ⏳ | — |
| 1.12.2 Forge | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 1.21.1 NeoForge | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | — |
| 26.1.2 NeoForge | ✅ | ✅ | ✅ | ✅ | ✅ | — (Patchouli bu sürüme çıkmadı) | — |

## Gerçek kurulum ve canlı hizmet sonuçları

* **1.12.2 gerçek kurulum (yayın jar'ı):** ✅ modsuz ve ✅ BetterQuesting + FTB Quests + Patchouli ile. Oyun ana menüye
  geldi, coremod jar'ın manifest'inden yüklendi, tüm denetimler geçti (dil dosyası, ekrandaki sabit yazı, üç mod).
* **Anahtarsız Google Çeviri, canlı:** ✅ örnek çıktılar: "Kill the Ender Dragon and collect its egg." →
  "Ender Ejderhasını öldür ve yumurtasını topla."; renk kodları (`§6…§r`), Patchouli komutları (`$(item)…$()`)
  ve `%s` korundu. Küçük kusurlar görüldü (ör. "%s FE" → "%sFE", hitapta "siz/sen" karışması).
* **Gemini / Groq / Mistral / OpenRouter:** gerçek hizmete karşı **sınanmadı** (CI'da anahtar yok). İstek/yanıt biçimi,
  kota hatası (429, günlük kota), yedek modele geçiş ve reddedilen parametrenin atılması yerel sahte sunucuyla birim
  testlerinde doğrulandı. Model adları ve ücretsiz sınırlar Ekim 2026 araştırmasına dayanır; Google günlük sınır
  sayılarını artık yayımlamıyor, bu yüzden sayılar kesin değildir.

## Doğrulanmayanlar (açıkça)

* **Gözle görsel kontrol yapılmadı.** Testler metnin doğru yere ulaştığını ölçer; uzun Türkçe metnin taşması, satır
  kaydırma ve sayfa görünümü bir insan tarafından bakılarak denetlenmedi. Bunun için `docs/MANUAL_TEST.md`.
* **Gerçek bir yapay zekâ modeliyle (Gemini vb.) çeviri kalitesi** ölçülmedi: CI'da anahtar yok. Yalnızca anahtarsız
  Google Çeviri canlı denendi (yukarıda).
* **Gerçek görev tamamlama** (eşya teslim etme, ödül alma) CI'da oynanmadı; görev verisinin değişmediği ölçüldü.
* **Fabric** modülü derlenmiyor ve sınanmadı.
* **Büyük mod paketleri** (yüzlerce mod) ve binlerce metinle performans sınanmadı.
* 1.16.5'te geliştirme ortamı (Architectury Loom) Forge'un yeniden adlandırdığı iki `Transformation` yöntemini
  bulamıyordu; yalnızca geliştirme çalıştırmasında devreye giren küçük bir düzeltme eklendi
  (`mixin/dev/TransformationDevMixin`, gerçek oyunda uygulanmaz). Gerçek bir 1.16.5 kurulumunda oyun içi deneme yapılmadı.
* `gradlew.bat` (Windows) çalıştırılmadı; aynı Gradle görevi Linux'ta `./gradlew` ile çalıştırıldı.
* Doku/resim içine çizilmiş yazılar ve sohbet mesajları bilerek çevrilmez.

## Jar dosyaları

* 1.20.1: `forge/build/libs/AutoTranslation-1.3.0+mc1.20.1-forge.jar` (CI çıktısı `forge-libs`).
* Diğer sürümler: `Other Minecraft versions` iş akışının `jar-<modül>` çıktıları.
