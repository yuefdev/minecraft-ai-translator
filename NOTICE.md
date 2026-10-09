# Atıf ve lisans / Attribution and license

Bu proje, **AutoTranslation** modunun (özgün yazar: **墨涤千尘**, depo: <https://github.com/Moirstral/AutoTranslation>,
eski adresi `LangYueMc/AutoTranslation`) değiştirilmiş bir sürümüdür.

This project is a modified version of the **AutoTranslation** mod (original author: **墨涤千尘**,
repository: <https://github.com/Moirstral/AutoTranslation>, formerly `LangYueMc/AutoTranslation`).

* Lisans / License: **GNU Affero General Public License v3.0** – tam metin [`LICENSE`](LICENSE) dosyasındadır ve
  değiştirilmemiştir. Bu sürümdeki tüm eklemeler de aynı lisansla (AGPL-3.0) sunulur.
  The full text is in [`LICENSE`](LICENSE), unchanged. All additions in this version are offered under the same
  license (AGPL-3.0).
* Özgün Git geçmişi (yazar adları ve tarihleriyle) bu depoda korunmuştur; değişiklikler üstüne eklenmiş commit'lerdir.
  The original Git history, with its authors and dates, is preserved; the changes are commits on top of it.
* `mods.toml` ve `README.md` içindeki özgün yazar ve proje bağlantıları korunmuştur.
  The original author and project links in `mods.toml` and `README.md` are kept.

## Bu sürümde eklenenler / What this version adds

* `core/` – Minecraft'tan bağımsız çeviri motoru: maskeleme/doğrulama, kalıcı önbellek, sözlük, OpenAI uyumlu sağlayıcı,
  kuyruk/hız sınırı/zaman aşımı/yeniden deneme. / Minecraft-independent translation engine.
* FTB Quests ve Patchouli için gösterim anı çevirisi (mixin). / Display-time translation for FTB Quests and Patchouli.
* Türkçe ayarlar ekranı ve `tr_tr` dil dosyası. / Turkish settings screen and `tr_tr` language file.
* Minecraft 1.20.1 / Forge 47 hedefi, CI iş akışları ve testler. / Minecraft 1.20.1 / Forge 47 target, CI and tests.

## Üçüncü taraf modlar / Third-party mods

FTB Quests, FTB Library, FTB Teams, Patchouli, Architectury API ve Cloth Config bu depoda **dağıtılmaz**. Yalnızca
derleme zamanında (ve CI duman testinde çalışma zamanında) kendi resmî Maven depolarından alınır. Her biri kendi
lisansına tabidir. / These mods are **not** redistributed here; they are only fetched from their official Maven
repositories at build time (and at run time in the CI smoke test). Each remains under its own license.
