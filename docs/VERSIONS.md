# Diğer Minecraft sürümleri

Ana teslimat **Minecraft 1.20.1 / Forge** sürümüdür (`forge/` modülü; Türkçe ayar ekranı, durum sayfası, sözlük ve
düzeltme düzenleyicisi dahil). Aynı çeviri motoru (`core/`) başka sürümlerde de çalışır; her sürüm `versions/`
altında küçük bir modüldür ve yalnızca o sürümün oyun/mod API'sine bağlanan "yapıştırıcı" kodu içerir.

> **Kural:** Bir sürüm yalnızca CI'da **derlendiyse ve oyun başlatılarak sınandıysa** (bkz.
> [`VERIFICATION.md`](VERIFICATION.md)) "doğrulanmış" sayılır. Sınanmayan hiçbir şey doğrulanmış gibi sunulmaz.

## Modüller

| Modül | Minecraft | Yükleyici | Java | Entegrasyonlar | Bağlanma yöntemi |
|---|---|---|---|---|---|
| `forge/` (+`common/`, `core/`) | 1.20.1 | Forge 47 | 17 | FTB Quests 2001, Patchouli 1.20.1 | Mixin |
| `versions/forge-1.19.2` | 1.19.2 | Forge 43 | 17 | FTB Quests 1902, Patchouli 1.19.2 | Mixin |
| `versions/forge-1.18.2` | 1.18.2 | Forge 40 | 17 | FTB Quests 1802, Patchouli 1.18.2 | Mixin |
| `versions/forge-1.16.5` | 1.16.5 | Forge 36 | 8+ | FTB Quests 1605, Patchouli 1.16.4 | Mixin |
| `versions/forge-1.12.2` | 1.12.2 | Forge 14.23.5 | 8 | BetterQuesting 3.5, FTB Quests 1.12, Patchouli 1.0 | ASM "coremod" (Mixin gerekmez) |
| `versions/neoforge-1.21.1` | 1.21.1 | NeoForge 21.1 | 21 | FTB Quests 2101, Patchouli 1.21.1 | Mixin |

## Ayarlar (1.20.1 dışındaki sürümler)

Bu sürümlerde oyun içi ayar ekranı **yoktur**; ayarlar dosyadan yapılır (dosyalar `oyun klasörü/AutoTranslation/` altında):

| Dosya | Ne için |
|---|---|
| `settings.json` | Otomatik çeviri, görev/kitap çevirisi, uç nokta, model, istek/dakika, zaman aşımı … (ilk açılışta varsayılanlarla oluşur) |
| ortam değişkeni `AUTOTRANSLATION_API_KEY` | API anahtarı (öncelikli). Alternatif: `secrets/api-key.txt` (dosyaya düz yazılır, `.gitignore`'dadır) |
| `glossary.json` | Terim sözlüğü (aynı terim hep aynı çevrilir) |
| `corrections.json` | Kendi düzeltmeleriniz (en yüksek öncelik) |
| `cache/translations.jsonl` | Kalıcı, çevrimdışı çalışan çeviri önbelleği |

Oyun dilini **Türkçe (tr_tr)** yapın; dil İngilizce ise hiçbir şey çevrilmez.

## Kurulum

1. İlgili sürümün jar'ını (`AutoTranslation-<sürüm>+mc<mc>-<yükleyici>.jar`) `mods/` klasörüne koyun.
2. İsteğe bağlı modları (BetterQuesting / FTB Quests / Patchouli) kurun – kurulu değillerse oyun yine açılır.
3. API anahtarını ortam değişkeniyle verin, `settings.json`'da uç noktayı ve modeli seçin.

Bu sürümlerde jar'lar GitHub Actions çıktısı olarak üretilir (`Other Minecraft versions` iş akışı, `jar-<modül>` dosyaları).
