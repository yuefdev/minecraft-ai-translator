# El ile oyun içi doğrulama listesi

Otomatik duman testi (CI) oyunu gerçekten başlatır ve entegrasyonun sınıflara uygulandığını ve boru hattının uçtan uca
çalıştığını kanıtlar; ancak **gözle görsel doğrulama** ve **gerçek görev tamamlama** otomatik değildir. Aşağıdaki listeyi
bir kez uygulayıp sonucu `docs/VERIFICATION.md` içindeki "El ile doğrulama" tablosuna işleyin.

## Hazırlık

1. Minecraft 1.20.1 + Forge 47.x profili. `mods/` içine: AutoTranslation jar'ı, Architectury API, Cloth Config,
   FTB Quests 2001.4.x (+ FTB Library 2001.2.x, FTB Teams 2001.3.x), Patchouli 1.20.1-8x ve içinde Patchouli kitabı olan bir mod.
2. Dil: **Türkçe (tr_tr)**. API anahtarı: `AUTOTRANSLATION_API_KEY` ortam değişkeni veya ayarlar ekranı.
3. Ayarlar → Sağlayıcı → **Bağlantıyı sına**: "Bağlantı başarılı" görmelisiniz.

## A. FTB Quests

| # | Adım | Beklenen |
|---|---|---|
| A1 | Görev kitabını açın | İlk anda İngilizce görünür, birkaç saniye içinde başlıklar Türkçeye döner (ekran kendini yeniler) |
| A2 | Bölüm listesinde bir bölümün üzerine gelin | Alt başlık ipucu Türkçe |
| A3 | Bir göreve tıklayın | Başlık, alt başlık ve uzun açıklama Türkçe; satır kaydırma ve sayfa geçişi (`{@pagebreak}`) düzgün; renk/biçim korunmuş |
| A4 | Görev hedefi/ödül başlıkları | Özel başlıklar Türkçe; eşya adları oyunun kendi `tr_tr` adlarıyla |
| A5 | Bir görevi **gerçekten tamamlayın** (hedef eşyayı teslim edin, ödülü alın) | Görev normal tamamlanır, ödül verilir, ilerleme çubuğu doğru artar |
| A6 | Oyundan çıkıp tekrar girin, ağı kapatın | Aynı metinler çevrimdışı da Türkçe (önbellek) |
| A7 | `ftbquests editing_mode` ile düzenleme modunu açın | Ham (çevrilmemiş) metin görünür; bir satırı düzenleyip kaydedin → kayıtlı metin İngilizce özgün metindir |
| A8 | Ayarlar → Düzeltmeler'e bir başlık için kendi çevirinizi girin | Görev ekranında anında sizin çeviriniz görünür |
| A9 | "Orijinali yanında göster" açın | Başlık/alt başlık/açıklama sonuna gri `(English)` eklenir |

## B. Patchouli

| # | Adım | Beklenen |
|---|---|---|
| B1 | Kitabı açın (kitap listesi, açılış sayfası) | Açılış metni ve kategori/giriş adları Türkçe |
| B2 | Uzun bir girişi açın | Metin Türkçe; **taşma/sayfalama düzgün** (yerleşim Türkçe metne göre) |
| B3 | `$(l:…)` bağlantılı ve `$(item)` biçimli bir metin | Bağlantılar tıklanır, renkler/biçimler korunur |
| B4 | Sayfa başlıkları (metin, resim, spotlight) | Türkçe |

## C. Mod kurulu değilken

| # | Adım | Beklenen |
|---|---|---|
| C1 | FTB Quests ve Patchouli'yi `mods/`'tan çıkarın, oyunu açın | Oyun hatasız açılır; `latest.log`'da AutoTranslation hatası yok; Durum sekmesi "yüklü değil" der |

## D. Güvenlik

| # | Adım | Beklenen |
|---|---|---|
| D1 | `logs/latest.log`, `debug.log`, `config/autotranslation.json5` içinde API anahtarınızı arayın | Hiçbirinde bulunmamalı |
| D2 | Yanlış anahtarla "Bağlantıyı sına" | Hata görünür, anahtar mesajda yok; kuyruk sonsuza dek denemez |

## E. Herhangi bir mod (özel entegrasyonu olmayan modlar)

Türkçe dil dosyası olmayan, rastgele seçilmiş birkaç mod kurun (ör. bir teknoloji modu ve bir dekorasyon modu).

| # | Adım | Beklenen |
|---|---|---|
| E1 | Modun eşyalarını envanterde/JEI'de görün | Eşya adları ve açıklama satırları birkaç saniye içinde Türkçe (ilk anda İngilizce görünür) |
| E2 | Modun kendi ekranını (makine arayüzü, ayar ekranı) açın | Ekrandaki sabit İngilizce yazılar Türkçe; düğmeler çalışır |
| E3 | Sohbete İngilizce bir şey yazın, bir tabelaya yazı yazın, bir dünyaya isim verin | **Çevrilmez** (oyuncunun yazdığı metinlere dokunulmaz) |
| E4 | Ayarlar → Genel → "Tüm ekranlardaki sabit yazılı metinleri çevir" seçeneğini kapatın | Sabit yazılar yalnızca elle açtığınız ekranlarda çevrilir; dil dosyası metinleri yine çevrilir |
