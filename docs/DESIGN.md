# Tasarım notları

## Katmanlar

```
core/   (saf Java 17, Minecraft'a bağımlı değil – birim testleri burada)
  lang/      LanguageProfile (tr_tr → tr), TextAnalyzer (Türkçe/İngilizce algılama)
  mask/      Masker: korunan öğeleri jetonla gizler, cevabı doğrular
  glossary/  Glossary, GlossaryStore, GameLanguageIndex (oyunun kendi tr_tr adları)
  cache/     TranslationCache (kalıcı), CacheKey, CorrectionStore (kullanıcı düzeltmeleri)
  provider/  TranslationProvider, OpenAiCompatibleProvider, PromptBuilder
  service/   TranslationService: öncelik, kuyruk, tekilleştirme, hız sınırı, zaman aşımı, yeniden deneme
common/ (Architectury; Minecraft'a bağlı yapıştırıcı)
  tr/        TranslationRuntime, TranslationBridge, FTB/Patchouli destek sınıfları
  mixin/compat/{ftbquests,patchouli}   isteğe bağlı entegrasyon mixinleri
  gui/       TranslatorSettingsScreen
forge/  (Forge girişi, mods.toml, jar paketleme: core jar'a gömülür)
```

## Çeviri önceliği

`TranslationService.lookup` bellekte, engellemeden şu sırayla çözer:

1. **Kullanıcı düzeltmesi** (`corrections.json`; kapsamlı düzeltme genel düzeltmeden önce gelir)
2. **Mevcut Türkçe karşılık** – dil anahtarları oyunun kendi `tr_tr` dosyasından çözülür (Patchouli/FTB metinlerinde
   `I18n` zaten Türkçe döndürür; dönen metin hâlâ İngilizce ise çevrilir). Ayrıca düz metin, oyunun resmî bir
   adıyla birebir örtüşüyorsa (`GameLanguageIndex`, belirsizse kullanılmaz) o ad kullanılır.
3. **Doğrulanmış önbellek** – yalnızca doğrulamadan geçmiş çeviriler yazılır.
4. **Yeni otomatik çeviri** – kuyruğa alınır, sonuç gelene kadar orijinal metin gösterilir.

## Veri ve biçim koruma

Sağlayıcıya gitmeden önce `Masker` şunları `[[P0]]`, `[[P1]]` … jetonlarıyla gizler:
`%s %d %1$s %%`, `{ad}`, `§x` ve `&x`/`&#rrggbb` renk kodları (FTB), `\n`/`\&` kaçışları (FTB), `{image:…}` gibi
FTB yer değiştirmeleri, URL'ler, `ad:yol` kimlikleri ve `#etiketler`, `/komutlar`, `` `kod` ``, Patchouli `$(…)`
komutları. Cevap şu koşullarda **reddedilir** (orijinal metin gösterilir, önbelleğe yazılmaz):

* bir jeton kayıp, çift veya uydurma;
* sayılar değişti (rakam dizilerinin çoklu kümesi), `3/10` / `12:30` gibi oran-ilerleme çiftlerinin sırası değişti;
* satır sayısı değişti; beklenmedik uzunluk; "Elbette, işte çeviri…" gibi yorum öneki;
* profilin biçim karakterleri sızdı (FTB'de çıplak `&`, `{`, `\`, `§`; Patchouli'de yeni `$(`);
* stilli (Component) metinde koşu etiketleri bozuldu.

Başarısız olan bir istem tek kez "katı" kipte yeniden denenir; yine başarısızsa o metin için 10 dakika yeniden
denenmez (arayüzdeki "Başarısızları yeniden dene" düğmesi bunu sıfırlar).

## Önbellek anahtarı

`SHA-256(kaynak metin hash'i, hedef dil, içerik kimliği, sağlayıcı kimliği (uç nokta ana makinesi), model, sözlük
sürümü, istem sürümü)`. Bunlardan biri değişince eski çeviri bulunamaz. Sözlük sürümü, düzenlenebilir
terimlerin içerik özetidir; oyun dil dosyalarından türetilen "ipuçları" sürümü değiştirmez (aksi hâlde bir
kaynak paketi eklemek tüm önbelleği çöpe atardı). Önbellek diske JSON-lines olarak eklenir, açılışta belleğe
okunur; arama sırasında ağa veya diske dokunulmaz, bu yüzden çevrimdışı çalışır.

## İş parçacıkları

* `lookup` yalnızca bellek kullanır; render iş parçacığında çağrılabilir. Sonuçlar `TranslationRuntime.generation()`
  ile anahtarlanmış bellek içi tablolarda tutulur, aynı metin her karede yeniden aranmaz.
* Ağ ve disk yazımı işçi iş parçacıklarında (`AutoTranslation-Worker-*`, `AutoTranslation-IO`) yapılır.
* Sonuç bildirimleri `Minecraft.execute` ile **istemci iş parçacığında** çalışır; açık ekran en çok 400 ms'de bir,
  toplu olarak yenilenir.

## Dayanıklılık

Sınırlı kuyruk (varsayılan 512; taşarsa `FAILED`), aynı istekler birleştirilir, dakikadaki istek sayısı sınırlıdır,
her çağrı zaman aşımına sahiptir, geçici hatalarda en fazla `maxRetries` (varsayılan 2) yeniden deneme yapılır
(`Retry-After` dikkate alınır, 30 sn ile sınırlı), kimlik doğrulama hataları yeniden denenmez, art arda 5 hata
sağlayıcıyı 60 sn duraklatır.

## `yourLanguageFeature` değerlendirmesi

Üst depodaki yaklaşım "metinde hedef dilin karakteri varsa çevrilmiş say" idi; varsayılan düzenli ifade Çince /
Japonca / Korece aralığıydı. Türkçe ve İngilizce aynı Latin alfabesini kullandığı için bu mantık Türkçede çalışmaz
(Türkçe harfsiz Türkçe metinler çevrilmemiş sayılır, İngilizce metinlerdeki tek bir `ö`/`ü` ise "zaten Türkçe"
sonucunu doğurur). `TextAnalyzer` üç kanıtı birleştirir: Türkçeye özgü harfler (ğ ı ş İ güçlü, ç ö ü zayıf),
Türkçe durak sözcükler (ASCII'ye indirgenmiş, yani "icin" de "için" de sayılır) ve İngilizce durak sözcükler.
`yourLanguageFeature` ayarı korunmuştur (varsayılanı artık `[çğıöşüÇĞİÖŞÜ]`) ama yalnızca **ek kanıt** sayılır,
tek başına karar vermez; bozuk bir düzenli ifade çeviriyi devre dışı bırakmaz.

## FTB Quests entegrasyonu (2001.4.x, Minecraft 1.20.1)

Bu sürümde görev metinleri ham `String` olarak tutulur ve sunucudan istemciye böyle gelir; ekrana
`QuestObjectBase.getTitle()`, `Quest.getSubtitle()`, `Quest.getDescription()` ile `Component`'e çevrilir.
Entegrasyon yalnızca bu üç gösterim yöntemine, ayrıca bölüm listesindeki alt başlık ipucuna takılır:

* `getRawTitle/getRawDescription/getRawSubtitle` (editörün kullandığı, yazılıp kaydedilen veri) **dokunulmaz**.
* Açıklama satır sayısı korunur (FTB satırlara ve sayfa kesmelerine indeksle erişir).
* Düzenleme modu açıkken (varsayılan) çeviri yapılmaz, böylece yazar gerçek metni görür.
* Kimlikler, bağımlılıklar, görev/ödül verisi ve ilerleme hiçbir şekilde okunmaz/yazılmaz.

## Patchouli entegrasyonu (1.20.1-8x)

* Sayfa/açılış/kategori metni `BookTextRenderer` kurucusunda, işaretleme ayrıştırılıp yerleşim hesaplanmadan **önce**
  çevrilir (`@ModifyArg` ile `BookTextParser.parse` argümanı). Kaydırma, sayfalama ve ölçekleme Türkçe metne göre
  hesaplanır.
* Giriş ve kategori adları ve sayfa başlıkları (`BookPage.i18n/i18nText`) üretildikleri yerde çevrilir.
* Çeviri sonradan gelirse açık kitap ekranı `resize` ile yeniden kurulur.
* Bilinen sınır: `$(t:…)` ipucu metinleri komutun parçası sayıldığı için çevrilmez.

## Opsiyonel bağımlılıklar

`AutoTranslationMixinPlugin`, FTB Quests/Patchouli mixinlerini yalnızca ilgili mod yüklüyse uygular ve
mixinler `@Pseudo` + `require = 0` ile işaretlidir; bir mod sürümü uyuşmazsa oyun çökmez, ilgili kanca devre dışı
kalır (ayarlar ekranının "Durum" sekmesi kancanın gerçekten çalışıp çalışmadığını gösterir).
