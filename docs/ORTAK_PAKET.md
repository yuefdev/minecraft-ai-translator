# Ortak çeviri paketi

Bir oyuncunun çevirdiği mod metinleri, diğer oyuncuların da kullanabileceği bir pakete dönüşür. Oyun, bir metni sağlayıcıya
(Google, Gemini …) göndermeden önce paketine bakar; paketteyse çeviri hemen gelir, istek harcanmaz. İlk açılışta
38 bin metin dakikalar yerine saniyeler içinde hazır olur.

## Güvenlik ve gizlilik

* **Yalnızca indirir.** Mod, paket sitesinden sabit dosyaları GET ile alır. Oyuncudan hiçbir şey gönderilmez; sunucu yalnızca
  sıradan bir web isteği (IP adresi, dil kodu) görür.
* **Pakette İngilizce metin yok.** Her satır yalnızca kaynak metnin SHA-256 özeti ve çeviridir.
* **Doğrulanır.** Her parça manifestteki SHA-256 ile eşleşmelidir. Her çeviri, orijinaldeki `%s`, `§` renk kodu, `{ad}` gibi
  parçaları aynı sayıda içermiyorsa gösterilmez; orijinal yerinde kalır ve sağlayıcıya sorulur.
* Paketi yalnızca siteyi yayınlayan kişi değiştirebilir. Oyuncu çevirileri otomatik yüklenmez (kötü niyetli çeviri
  eklenemez).
* İstemeyen `settings.json` içinde `"sharedPacks": false` yazar.

## Oyuncu tarafı

`AutoTranslation/settings.json`:

```json
"sharedPacks": true,
"sharedPackUrl": "https://paket-siteniz.pages.dev"
```

Adres sürümle birlikte gelir (`EngineSettings.DEFAULT_PACK_URL`); elle yazmak yalnızca farklı bir site için gerekir.
Dosyalar `AutoTranslation/packs/<dil>/` içinde saklanır, çevrimdışıyken de kullanılır.

## Paketi yayınlayan tarafı

1. Çeviri yapılmış bir oyundan `AutoTranslation/cache/translations.jsonl` dosyasını al.
2. Paketi üret (Gson gerekir; Minecraft'ın `libraries` klasöründeki `gson-*.jar` yeter):

   ```bash
   java -cp "AutoTranslation-1.3.0+mc1.12.2-forge.jar;gson.jar" me.langyue.autotranslation.core.pack.PackBuilder translations.jsonl tr_tr pack-site
   ```

   Aynı klasörde yeniden çalıştırmak pakete ekler; farklı oyuncuların önbellekleri birleştirilebilir. Yalnızca dil dosyası ve
   arayüz metinleri girer; görev ve kitap metinleri girmez.
3. `pack-site/` içeriğini depoya ekle (`pack-site/v1/tr_tr/manifest.json` ve `shard-*.jsonl.gz`).
4. Cloudflare Pages: depoyu bağla, **build command** boş, **output directory** `pack-site`. Yayın adresi
   `https://<proje>.pages.dev` olur.
5. Bu adresi `EngineSettings.DEFAULT_PACK_URL` içine yaz ve yeni jar'ı derle.

Not: paket, modların metinlerinden türetilmiş çeviriler içerir. Bazı mod yazarları bunun yeniden dağıtılmasını
istemeyebilir; yayınlamadan önce bunu göz önünde bulundur.
