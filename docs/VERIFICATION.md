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
başlatma), `versions.yml` (diğer sürümler ve Fabric modülleri: derleme + oyun başlatma). 1.20.1 Fabric, `forge-build.yml` ve
`smoke.yml` içinde Forge ile birlikte çalışır.

## Sonuçlar

Durum işaretleri: ✅ CI'da geçti · ❌ CI'da başarısız · ⏳ sonuç bekleniyor · — uygulanamaz

Son tam çalıştırma: tüm sürümler için derleme + iki oyun açılışı (18 iş) başarılı. FTB Quests 1.16.5/1.18.2/1.19.2'de
zorunlu bağımlılığı **Item Filters**'ı ister; testlerde CurseForge'daki ilgili sürümü kullanılır (oyuncunun modpaketinde
zaten vardır).

| Sürüm | Derleme | Oyun açılışı (yalnız bu mod) | Her mod: dil dosyası | Her mod: ekrandaki sabit yazı | FTB Quests | Patchouli | BetterQuesting |
|---|---|---|---|---|---|---|---|
| 1.20.1 Forge (ana sürüm) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | — |
| 1.19.2 Forge | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | — |
| 1.18.2 Forge | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | — |
| 1.16.5 Forge | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | — |
| 1.12.2 Forge | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 1.21.1 NeoForge | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | — |
| 26.1.2 NeoForge | ✅ | ✅ | ✅ | ✅ | ✅ | — (Patchouli bu sürüme çıkmadı) | — |
| 1.20.1 Fabric | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | — |
| 1.21.1 Fabric | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | — |
| 26.1.2 Fabric | ✅ | ✅ | ✅ | ✅ | ✅ | — (Patchouli bu sürüme çıkmadı) | — |
| 1.19.2 Fabric | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | — |
| 1.18.2 Fabric | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | — |
| 1.16.5 Fabric | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | — |

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
* **Fabric** sürümleri: tablodaki satırlar 2026-11-08 CI çalıştırmasında (33 iş) geçti. Altı Fabric
  sürümünün `runSmokeClient -PsmokeMods` testi 2026-11-07'de Windows'ta yerelde geçti (FTB Quests ve, 26.1.2 dışında,
  Patchouli yüklü; tüm kontroller ✅). İlk CI çalıştırması "yalnız bu mod" testinde Fabric API olmadan modun dil
  dosyalarının yüklenmediğini gösterdi; Fabric API artık zorunlu bağımlılık. 1.12.2 için Fabric sürümü yoktur.
* **1.20.1 Fabric gerçek kurulum, 2026-11-08:** ✅ Yayın jar'ı; Fabric Loader 0.17.3, Fabric API 0.92.12, Architectury,
  Cloth Config, Mod Menu 7.2.2, FTB Quests 2001.4.22 ve Patchouli 1.20.1-85 (hepsi yayımlanmış jar'lar), oyun dili Türkçe,
  anahtarsız Google. Bir test yardımcısı dünya kurdu, görev kitabını ve bir Patchouli kitabını açıp tıkladı, ekran görüntüsü
  aldı. Görev başlığı/alt başlığı/açıklaması, bölüm adı, kitap açılış metni, kategori adı ve açıklaması, giriş adı, sayfa
  başlığı ve sayfa metni Türkçe göründü; `$(item)…$()` ve `$(br2)` işaretlemesi korundu. Bu test, Patchouli sayfa metni
  kancasının gerçek kurulumda çalışmadığını ortaya çıkardı (geliştirme ortamında çalışıyordu); düzeltildi.
  Bilinen durumlar: bir ekran ilk açıldığında metin İngilizce görünür, çeviri gelince ekran yeniden açıldığında Türkçe
  olur. Anahtarsız Google kısa başlıklarda anlamı kaçırabiliyor: "Smelt Your First Iron" → "İlk Ütünüzü Koklayın".
  Diğer Fabric sürümleri gerçek kurulumda denenmedi.
* **Büyük mod paketleri** (yüzlerce mod) ve binlerce metinle performans sınanmadı.
* 1.16.5'te geliştirme ortamı (Architectury Loom) Forge'un yeniden adlandırdığı iki `Transformation` yöntemini
  bulamıyordu; yalnızca geliştirme çalıştırmasında devreye giren küçük bir düzeltme eklendi
  (`mixin/dev/TransformationDevMixin`, gerçek oyunda uygulanmaz). Gerçek bir 1.16.5 kurulumunda oyun içi deneme yapılmadı.
* `gradlew.bat` (Windows) çalıştırılmadı; aynı Gradle görevi Linux'ta `./gradlew` ile çalıştırıldı.
* Doku/resim içine çizilmiş yazılar ve sohbet mesajları bilerek çevrilmez.

## Jar dosyaları

* 1.20.1: `forge/build/libs/AutoTranslation-1.4.0+mc1.20.1-forge.jar` (CI çıktısı `forge-libs`).
* Diğer sürümler: `Other Minecraft versions` iş akışının `jar-<modül>` çıktıları.
