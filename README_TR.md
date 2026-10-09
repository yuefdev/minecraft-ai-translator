# AutoTranslation – Türkçe çeviri hattı (ana sürüm: Minecraft 1.20.1, Forge)

Mod paketindeki İngilizce içeriği, bağlamı anlayarak doğal Türkçeye çeviren **istemci taraflı** bir moddur.
Özgün proje: [Moirstral/AutoTranslation](https://github.com/Moirstral/AutoTranslation) (AGPL-3.0, atıflar için bkz.
[`NOTICE.md`](NOTICE.md)). Bu sürüm üstüne şunları ekler:

* **Ücretsiz çeviri, kurulumsuz:** anahtar girmezseniz anahtarsız Google Çeviri kullanılır (resmi olmayan uç nokta).
  **Ücretsiz ve daha kaliteli:** Google Gemini'nin ücretsiz anahtarı (kart gerekmez). Ayrıca Groq, Mistral,
  OpenRouter ücretsiz modelleri, bilgisayarda çalışan Ollama ya da herhangi bir OpenAI uyumlu (ücretli) hizmet.
  Anahtarı yapıştırmanız yeter: hangi hizmet olduğu anahtardan anlaşılır.
* **FTB Quests**: bölüm/görev başlıkları, alt başlıklar, uzun açıklamalar, hedef ve ödül başlıkları, bölüm alt başlığı ipuçları.
* **Patchouli**: kitap/kategori/giriş başlıkları, sayfa metinleri, listeler, sayfa başlıkları – yerleşim hesaplanmadan önce.
* **Her modun İngilizce metni, otomatik olarak** – modun adı bir listede olmasa da:
  * dil dosyasındaki metinler (Türkçe dosyası olmayan modların eşya/blok adları, açıklama satırları, arayüz yazıları),
  * ekranlarda koda gömülü (sabit yazılmış) İngilizce yazılar – varsayılan olarak **tüm ekranlarda** açıktır.
  Sohbet, tabela, kitap ve isim verme ekranları gibi **oyuncunun yazdığı metinler çevrilmez**.
* Kalıcı, **çevrimdışı çalışan** önbellek; düzenlenebilir sözlük; yerel düzeltmeler; Türkçe ayarlar ekranı.

Bu mod, oyun verisini **yeniden yazmaz**: çeviri yalnızca ekrana çizilirken yapılır. Görev kimlikleri, koşullar, ödüller,
komutlar, sayılar ve ilerleme olduğu gibi kalır.

## Kurulum

Gerekli: Minecraft **1.20.1**, Forge **47.x**, Java 17, [Architectury API](https://modrinth.com/mod/architectury-api) ve
[Cloth Config](https://modrinth.com/mod/cloth-config). İsteğe bağlı (yüklüyse çevrilir, yüklü değilse oyun normal açılır):
**FTB Quests** (+ FTB Library, FTB Teams) ve **Patchouli**.

1. `AutoTranslation-<sürüm>+mc1.20.1-forge.jar` dosyasını `mods/` klasörüne koyun.
2. Oyunda dili **Türkçe (tr_tr)** yapın.
3. Hiçbir şey yapmasanız da çeviri çalışır (anahtarsız Google Çeviri). Daha iyi Türkçe için ücretsiz bir
   **Gemini anahtarı** alın (<https://aistudio.google.com/apikey>; `AQ.…` veya `AIza…` ile başlar) ve girin – **dosyaya düz yazılmaz**:
   * ortam değişkeni `AUTOTRANSLATION_API_KEY` (öncelikli), ya da
   * Mod menüsü → AutoTranslation → **Sağlayıcı** sekmesi → API anahtarı (yalnızca
     `oyun klasörü/AutoTranslation/secrets/api-key.txt` içine, sahibine özel izinle yazılır; klasör `.gitignore`'dadır).
4. **Sağlayıcı** sekmesinde **Hizmet** "Otomatik" kalabilir (anahtara göre seçilir) ya da listeden bir hizmet seçin;
   **Ücretsiz anahtar al** düğmesi anahtar sayfasını açar. **Bağlantıyı sına** ile deneyin.

1.12.2 için adım adım anlatım: [`docs/KURULUM_1.12.2.md`](docs/KURULUM_1.12.2.md).

Yerel model (Ollama) için: uç nokta `http://localhost:11434/v1`, model örn. `llama3.1`, anahtar gerekmez.

## Ayarlar ekranı

Mod menüsünden (Forge: *Mods → AutoTranslation → Config*) açılır.

| Sekme | İçerik |
|---|---|
| Genel | Otomatik çeviri, görev çevirisi, kitap çevirisi, eşya/arayüz çevirisi, orijinali yanında göster, düzenleme modunda çevirme, toplu dil dosyası çevirisi |
| Sağlayıcı | Uç nokta, model, API anahtarı (maskeli), istek/dakika, zaman aşımı, yeniden deneme, bağlantı sınaması |
| Sözlük | Düzenlenebilir terim listesi (aynı eşya/mekanik hep aynı adla çevrilir) |
| Düzeltmeler | Belirli bir İngilizce metin için kendi Türkçe çevirinizi yazın; en yüksek önceliklidir |
| Durum | Kuyruk, çevrilen/başarısız sayısı, önbellek, son hata, FTB Quests/Patchouli kancalarının durumu, "başarısızları yeniden dene" |

Dosyalar `oyun klasörü/AutoTranslation/` altındadır: `glossary.json`, `corrections.json`, `cache/translations.jsonl`.

## Maliyet ve performans

Aynı anda istenen kısa metinler tek istekte birlikte gönderilir (ücretsiz katmanların dakikadaki istek sınırı için).
Ücretsiz bir hizmetin günlük sınırı dolunca önce aynı hizmetin yedek modeline geçilir, o da dolarsa yeni çeviriler bir
süre bekletilir; önbellekteki çeviriler görünmeye devam eder.
Metinler yalnızca **ilk görüntülendiklerinde** istenir ve sonuç önbelleğe yazılır; tüm modpaketi başlangıçta
çevrilmez (üst projenin toplu çevirisi **varsayılan olarak kapalıdır**, çünkü binlerce istek ve ücret doğurabilir).
Dakikadaki istek sayısı, kuyruk büyüklüğü, zaman aşımı ve yeniden deneme sayısı sınırlıdır.

## Bilinen sınırlar

* Ham `String` yerine dil anahtarı kullanan görev dosyalarında Türkçe karşılık oyunun/modun `tr_tr` dosyasından gelir;
  dosya yoksa İngilizce kalır ve çevrilir.
* Patchouli `$(t:…)` ipucu metinleri çevrilmez.
* Sayıların sırası serbesttir (cümle yapısı değişebilir) ama `3/10`, `12:30` gibi çiftlerin sırası değişirse çeviri reddedilir.
* BetterQuesting yalnızca **1.12.2** modülünde desteklenir (mod daha yeni sürümlere çıkmadı).
* Doku/resim içine çizilmiş yazılar ve sohbet mesajları çevrilmez.
* Diğer Minecraft sürümleri (1.12.2 – 26.1.2) için bkz. [`docs/VERSIONS.md`](docs/VERSIONS.md).
* Fabric modülü depoda durur ama bu sürümde derlenmez ve doğrulanmamıştır (`-PwithFabric=true` ile açılabilir).

## Derleme

```
./gradlew :core:test       # ağ gerektirmeyen birim testleri (Maven Central yeterli)
./gradlew :forge:build     # Windows: gradlew.bat :forge:build  → forge/build/libs/
```

Ayrıntılı tasarım: [`docs/DESIGN.md`](docs/DESIGN.md). Neyin nasıl doğrulandığı: [`docs/VERIFICATION.md`](docs/VERIFICATION.md).
