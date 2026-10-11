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
"sharedPackUrl": "https://autotranslation-packs.ergyusuf34.workers.dev"
```

Adres sürümle birlikte gelir (`EngineSettings.DEFAULT_PACK_URL`); elle yazmak yalnızca farklı bir site için gerekir.
Dosyalar `AutoTranslation/packs/<dil>/` içinde saklanır, çevrimdışıyken de kullanılır.

## Paketi yayınlayan tarafı

Site, alan adı gerektirmeyen bir Cloudflare Worker'dır (statik dosyalar): `https://autotranslation-packs.ergyusuf34.workers.dev`.
Ayarı `wrangler.jsonc` içindedir; adres `EngineSettings.DEFAULT_PACK_URL` olarak jar'a yazılıdır.

1. Çeviri yapılmış bir oyundan `AutoTranslation/cache/translations.jsonl` dosyasını al.
2. Paketi üret (Gson gerekir; Minecraft'ın `libraries` klasöründeki `gson-*.jar` yeter):

   ```bash
   java -cp "AutoTranslation-1.3.0+mc1.12.2-forge.jar;gson.jar" me.langyue.autotranslation.core.pack.PackBuilder translations.jsonl tr_tr pack-site
   ```

   Aynı klasörde yeniden çalıştırmak pakete ekler; farklı oyuncuların önbellekleri birleştirilebilir. Yalnızca dil dosyası ve
   arayüz metinleri girer; görev ve kitap metinleri girmez.
3. Yayınla (Cloudflare hesabına `wrangler login` ile girilmiş olmalı):

   ```bash
   npx wrangler deploy
   ```

4. `pack-site/v1/<dil>/` içindeki değişiklikleri depoya da ekle.

Not: paket, modların metinlerinden türetilmiş çeviriler içerir. Bazı mod yazarları bunun yeniden dağıtılmasını
istemeyebilir; yayınlamadan önce bunu göz önünde bulundur.
