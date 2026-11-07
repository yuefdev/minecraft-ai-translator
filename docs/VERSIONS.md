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
| `versions/neoforge-26.1.2` | 26.1.2 | NeoForge 26.1.2 | 25 | FTB Quests 26.1.2 (Patchouli bu sürüme çıkmadı) | Mixin |
| `fabric/` (+`common/`, `core/`) | 1.20.1 | Fabric Loader + Fabric API | 17 | FTB Quests 2001, Patchouli 1.20.1 | Mixin (`forge/` entegrasyon kodu ortak) |
| `versions/fabric-1.21.1` | 1.21.1 | Fabric Loader | 21 | FTB Quests 2101, Patchouli 1.21.1 | Mixin (`neoforge-1.21.1` kodu + `fabric-common`) |
| `versions/fabric-26.1.2` | 26.1.2 | Fabric Loader | 25 | FTB Quests 26.1.2 | Mixin (`neoforge-26.1.2` kodu + `fabric-common`) |
| `versions/fabric-1.19.2` | 1.19.2 | Fabric Loader | 17 | FTB Quests 1902, Patchouli 1.19.2 | Mixin (`forge-1.19.2` kodu + `fabric-common`) |
| `versions/fabric-1.18.2` | 1.18.2 | Fabric Loader | 17 | FTB Quests 1802, Patchouli 1.18.2 | Mixin (`forge-1.18.2` kodu + `fabric-common`) |
| `versions/fabric-1.16.5` | 1.16.5 | Fabric Loader | 8+ | FTB Quests 1605, Patchouli 1.16.4 | Mixin (`forge-1.16.5` kodu + `fabric-common`) |

Her sürümde, mod adı bilinmeden çalışan **genel çeviri** de vardır: Türkçe dosyası olmayan modların dil dosyası metinleri
ve ekranlara koda gömülü İngilizce yazılar (sohbet, tabela, kitap, isim verme ekranları hariç). 1.12.2'de bu, oyunun
`Locale`/`LanguageMap` ve `FontRenderer` sınıflarına ASM ile eklenen çağrılarla yapılır.

## Fabric

Fabric sürümleri ayrı bir kod kopyası değildir: `versions/fabric-<sürüm>` modülü, aynı Minecraft sürümünün
Forge/NeoForge modülünün kaynaklarını (yalnızca o yükleyicinin giriş sınıfı `AutoTranslationMod` hariç) ve
`versions/fabric-common/` altındaki küçük Fabric giriş kodunu derler. Yükleyiciye özgü üç şey (oyun klasörü, kurulu
modlar, istemci tick'i) `ClientInit.init(...)`'e parametre olarak verilir; Fabric'te tick, `Minecraft.tick()` sonuna
eklenen bir mixin'le alınır, bu yüzden bu sürümler **Fabric API istemez** (yalnızca Fabric Loader). 1.20.1'de
`fabric/` modülü Architectury ile `common/`'u paylaşır; FTB Quests/Patchouli entegrasyonu `forge/` modülünden ortak
derlenir. Fabric modülleri Gradle 9.8 + Fabric Loom 1.19 ile derlenir (1.16.5–1.21.1 `fabric-loom-remap`, 26.1.2
`fabric-loom`).

**1.12.2 için Fabric sürümü yoktur:** resmi Fabric 1.14'ten başlar; 1.12.2 yalnızca topluluk projesi Legacy Fabric ile
mümkündür, orada Mojang adları yoktur (1.12.2 modülünün ASM kancaları baştan yazılmalıdır) ve BetterQuesting,
FTB Quests, Patchouli 1.12.2'de yalnızca Forge için çıkmıştır.

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
