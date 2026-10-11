# Atıf ve lisans / Attribution and licence

## Türkçe

Bu proje, **AutoTranslation** modunu (özgün yazar: **墨涤千尘**, depo: <https://github.com/Moirstral/AutoTranslation>,
eski adresi `LangYueMc/AutoTranslation`) temel alır ve üstüne yeni bölümler ekler.

* **Lisans:** GNU Affero General Public License v3.0. Tam metin [`LICENSE`](LICENSE) dosyasındadır ve
  değiştirilmemiştir. Bu depodaki tüm eklemeler de aynı lisansla (AGPL-3.0) sunulur.
* **Özgün kaynaktan gelenler:** `common/`, `forge/` ve `fabric/` (Minecraft 1.20.1 modülü) özgün projeden alınmış ve
  değiştirilmiştir. Java paket adı (`me.langyue.autotranslation`) özgün projeden korunmuştur.
* **Bu projede yazılanlar:** `core/` (Minecraft'tan bağımsız çeviri motoru), `versions/` (1.12.2, 1.16.5, 1.18.2,
  1.19.2, 1.21.1 ve 26.1.2 modülleri), Türkçe çeviri hattı, FTB Quests / Patchouli / BetterQuesting entegrasyonları,
  toplu ön çeviri, ortak çeviri paketleri, testler ve CI iş akışları.
* **Geçmiş:** Özgün projenin Git geçmişi bu depoya **aktarılmamıştır**; bu depo özgün kodun bir anlık görüntüsüyle
  başlar. Özgün yazarlar ve tarihler için özgün depoya bakın.
* `mods.toml` ve `fabric.mod.json` içinde özgün yazarın adı korunmuştur.

## English

This project is based on the **AutoTranslation** mod (original author: **墨涤千尘**, repository:
<https://github.com/Moirstral/AutoTranslation>, formerly `LangYueMc/AutoTranslation`) and adds new parts on top of it.

* **Licence:** GNU Affero General Public License v3.0. The full text is in [`LICENSE`](LICENSE), unchanged. All
  additions in this repository are offered under the same licence (AGPL-3.0).
* **From the original:** `common/`, `forge/` and `fabric/` (the Minecraft 1.20.1 module) were taken from the original
  project and modified. The Java package name (`me.langyue.autotranslation`) is kept from the original.
* **Written in this project:** `core/` (the Minecraft-independent translation engine), `versions/` (the 1.12.2, 1.16.5,
  1.18.2, 1.19.2, 1.21.1 and 26.1.2 modules), the Turkish pipeline, the FTB Quests / Patchouli / BetterQuesting
  integrations, bulk pre-translation, shared translation packs, tests and CI workflows.
* **History:** the Git history of the original project is **not** included here; this repository starts from a
  snapshot of the original code. See the original repository for its authors and dates.
* The original author's name is kept in `mods.toml` and `fabric.mod.json`.

## Üçüncü taraf modlar / Third-party mods

FTB Quests, FTB Library, FTB Teams, Patchouli, BetterQuesting, Architectury API ve Cloth Config bu depoda
**dağıtılmaz**. Yalnızca derleme zamanında (ve CI duman testinde çalışma zamanında) kendi resmî depolarından alınır.
Her biri kendi lisansına tabidir. / These mods are **not** redistributed here; they are only fetched from their
official repositories at build time (and at run time in the CI smoke test). Each remains under its own licence.
