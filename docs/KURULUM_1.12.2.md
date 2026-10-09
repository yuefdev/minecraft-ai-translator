# Minecraft 1.12.2 kurulumu (adım adım)

## 1. Gerekenler

* Minecraft **1.12.2** ve Forge **14.23.5.2859** veya daha yenisi (Java 8 ile çalışır; çoğu başlatıcı bunu kendisi ayarlar).
* İsteğe bağlı: **BetterQuesting**, **FTB Quests (1.12)**, **Patchouli (1.12)**. Kurulu değilseler oyun yine açılır.

## 2. Jar dosyasını indirin

1. GitHub'da depo → **Actions** → **Other Minecraft versions** (veya **Real 1.12.2 install**) iş akışının en son başarılı çalışmasını açın.
2. Sayfanın altındaki **Artifacts** bölümünden **`jar-forge-1.12.2`** dosyasını indirip zip'i açın.
3. İçinden **`AutoTranslation-1.3.0+mc1.12.2-forge.jar`** dosyasını alın. Adında `-dev` geçen dosya **değil**: o yalnızca geliştirme ortamı içindir.

## 3. Kurun

1. Jar'ı oyun klasörünüzdeki **`mods/`** klasörüne koyun.
2. Oyunu açın: **Seçenekler → Dil → Türkçe**.

Bu kadar. Ek ayar yapmazsanız mod **anahtarsız, ücretsiz Google Çeviri** ile hemen çalışır.

## 4. Daha iyi çeviri: ücretsiz Gemini anahtarı (önerilen)

Google Çeviri hızlıdır, ama bağlamı bilmez ve hitap biçimini karıştırabilir. Gemini (Google'ın yapay zekâsı)
cümleyi bağlamıyla çevirir. Ücretsiz katman için kredi kartı **gerekmez**.

1. <https://aistudio.google.com/apikey> adresine Google hesabınızla girin ve **API anahtarı oluşturun**.
   Yeni anahtarlar `AQ.…` ile, eskiler `AIza…` ile başlar. İkisi de olur.
2. Oyun klasöründe **`AutoTranslation/secrets/`** klasörünü oluşturun (yoksa) ve içine **`api-key.txt`** adlı bir dosya
   açın. Dosyaya yalnızca anahtarı yapıştırıp kaydedin.
   * Alternatif: `AUTOTRANSLATION_API_KEY` ortam değişkeni.
3. Oyunu yeniden başlatın. Mod anahtarın biçiminden Gemini olduğunu anlar ve kendiliğinden Gemini'ye geçer.

Anahtar dosyası hiçbir log'a yazılmaz. Dosyayı kimseyle paylaşmayın.

### Diğer ücretsiz seçenekler

`AutoTranslation/settings.json` dosyasındaki `"provider"` alanı hangi hizmetin kullanılacağını belirler. Dosya ilk
açılışta oluşur; bu alan dosyada yoksa elle ekleyin (ör. `"provider": "gemini",`). Eklemezseniz `"auto"` geçerlidir:

| Değer | Ne yapar |
|---|---|
| `"auto"` (varsayılan) | Anahtara bakar: `AQ.…` / `AIza…` → Gemini, `gsk_…` → Groq, `sk-or-…` → OpenRouter. Anahtar yoksa anahtarsız Google Çeviri. |
| `"google-free"` | Her zaman anahtarsız Google Çeviri (resmi olmayan uç nokta; Google çok istek görürse bir süre engelleyebilir). |
| `"gemini"` | Gemini, ücretsiz anahtar. |
| `"groq"` | Groq, ücretsiz anahtar (<https://console.groq.com/keys>). |
| `"mistral"` | Mistral, ücretsiz anahtar (telefon doğrulaması ister). |
| `"openrouter"` | OpenRouter'ın ücretsiz modelleri (günde 50 istek; hesap ayarlarında ücretsiz uç noktalara izin verilmeli). |
| `"ollama"` | Bilgisayarınızda çalışan Ollama (internet ve anahtar gerekmez; güçlü bir bilgisayar ister). |
| `"custom"` | `endpoint` ve `model` alanlarındaki OpenAI uyumlu adres (ör. ücretli OpenAI). |

## 5. Ne beklemeli

* Bir metin **ilk görüldüğünde** birkaç saniye İngilizce kalır, sonra Türkçeye döner. Çeviriler
  `AutoTranslation/cache/` içine kaydedilir; bir sonraki açılışta internet olmadan da Türkçe görünür.
* Aynı anda istenen kısa metinler (eşya adları, düğmeler) **tek istekte** birlikte gönderilir. Ücretsiz katmanların
  dakikadaki istek sınırı bu yüzden çabuk dolmaz.
* Ücretsiz Gemini'nin **günlük** bir sınırı vardır (yaklaşık birkaç yüz istek; kendi sınırınızı
  <https://aistudio.google.com/rate-limit> adresinde görebilirsiniz). Sınır dolunca mod önce aynı hizmetin yedek
  modeline geçer. O da dolarsa yeni metinleri bu arada anahtarsız Google Çeviri ile çevirir (`"keylessFallback": false`
  ile kapatılabilir). Önceden çevrilmiş metinler görünmeye devam eder.
* Sohbet, tabela, kitap ve isim verme ekranlarında **oyuncunun yazdığı metinler** çevrilmez.

## 6. Sorun olursa

* `logs/fml-client-latest.log` içinde `AutoTranslation` geçen satırlara bakın: hangi hizmetin kullanıldığı ve son
  hata orada yazar (anahtar asla yazılmaz).
* Çeviri hiç gelmiyorsa: oyun dili Türkçe mi, `settings.json` içinde `"enabled": true` mi?
* Gemini "quota" hatası veriyorsa günlük ücretsiz sınır dolmuştur. Pasifik saatiyle gece yarısı sıfırlanır: Türkiye
  saatiyle yazın 10:00, kışın (Kasım–Mart) 11:00.
* "PKIX" veya "certificate" hatası görürseniz başlatıcı çok eski bir Java 8 kullanıyordur. Profilde güncel bir Java 8
  seçin. Google hizmetleri eski Java ile de çalışır; Groq, OpenRouter ve Mistral'de sorun çıkabilir.
* Gemini "API key was not accepted" diyorsa anahtar yanlış kopyalanmıştır: dosyada yalnızca anahtar olmalı.
