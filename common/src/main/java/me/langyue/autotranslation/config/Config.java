package me.langyue.autotranslation.config;

import me.langyue.autotranslation.AutoTranslation;
import me.langyue.autotranslation.translate.TranslatorManager;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Comment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@me.shedaniel.autoconfig.annotation.Config(name = AutoTranslation.MOD_ID)
public class Config implements ConfigData {

    public enum FilterMode {
        RESOURCE,
        CORRECTION
    }

    public enum ScreenArea {
        TOP_LEFT,
        TOP_CENTER,
        TOP_RIGHT,
        MIDDLE_LEFT,
        MIDDLE_CENTER,
        MIDDLE_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_CENTER,
        BOTTOM_RIGHT
    }

    @Comment("筛选模式\n  RESOURCE: 只要当前语言存在 key，就忽略这个key，无论是否翻译\n  CORRECTION: 只要当前语言的 key 未翻译，就进行翻译，无论资源文件内是否存在")
    public FilterMode mode = FilterMode.RESOURCE;

    @Comment("英语特征")
    public String enFeature = "([A-Z]?[a-z]{2,}\\s*)+";

    @Comment("Hedef dile ait ek ipucu (düzenli ifade). Türkçe ve İngilizce aynı Latin alfabesini kullandığı için tek başına\n"
            + "karar vermez; yalnızca durak sözcük ve Türkçe harf kanıtlarına ek kanıt olarak sayılır.\n"
            + "Eski varsayılan (Çince/Japonca/Korece) Türkçe için geçersizdir.")
    public String yourLanguageFeature = "[çğıöşüÇĞİÖŞÜ]";

    @Comment("Çeviri motoru: OpenAI (OpenAI uyumlu sağlayıcı, önerilen) veya Google (eski, yalnızca genel metinler için)")
    public String translator = TranslatorManager.OPENAI_TRANSLATOR;

    @Comment("Türkçe çeviri hattı (OpenAI uyumlu sağlayıcı). API anahtarı BURADA TUTULMAZ; ortam değişkeni\n"
            + "AUTOTRANSLATION_API_KEY veya AutoTranslation/secrets/api-key.txt dosyası kullanılır.")
    @ConfigEntry.Gui.NoTooltip
    @ConfigEntry.Gui.TransitiveObject
    @ConfigEntry.Category("turkish")
    @ConfigEntry.Gui.PrefixText
    public Turkish turkish = new Turkish();

    public static class Turkish {
        @Comment("Otomatik çeviriyi aç/kapat")
        public boolean enabled = true;

        @Comment("FTB Quests görev, bölüm, hedef ve ödül metinlerini çevir")
        public boolean translateQuests = true;

        @Comment("Patchouli rehber kitaplarını çevir")
        public boolean translateBooks = true;

        @Comment("Dil dosyası anahtarlarını, eşya açıklamalarını ve mod arayüz metinlerini çevir")
        public boolean translateGeneric = true;

        @Comment("Her modun ekranındaki sabit yazılı İngilizce metinleri kendiliğinden çevir (ekran simgesiyle tek tek kapatılabilir). Kapalıysa yalnızca simgeyle açtığınız ekranlar çevrilir")
        public boolean translateAllScreens = true;

        @Comment("Türkçe metnin yanında orijinal İngilizce metni de göster")
        public boolean showOriginal = false;

        @Comment("Düzenleme modundayken (FTB Quests) çeviri gösterme")
        public boolean pauseWhileEditingQuests = true;

        @Comment("Başlangıçta tüm çevrilmemiş dil dosyalarını toplu çevir (çok sayıda istek oluşturur ve ücret doğurabilir)")
        public boolean bulkLanguageFiles = false;

        @Comment("Çeviri hizmeti: auto (anahtara göre seçilir; anahtar yoksa ücretsiz, anahtarsız Google Çeviri), google-free, gemini (ücretsiz anahtar), groq, openrouter, ollama (bilgisayarınızda) veya custom (aşağıdaki uç nokta ve model)")
        public String provider = "auto";

        @Comment("Ücretsiz yapay zekâ hizmetinin günlük kotası dolunca yeni metinleri bu arada anahtarsız Google Çeviri ile çevir")
        public boolean keylessFallback = true;

        @Comment("Yalnızca 'custom' hizmette: OpenAI uyumlu uç nokta (taban adres), örn. https://api.openai.com/v1 veya http://localhost:11434/v1")
        public String endpoint = "https://api.openai.com/v1";

        @Comment("Yalnızca 'custom' hizmette: model adı")
        public String model = "gpt-4o-mini";

        @Comment("Dakikadaki en çok istek sayısı")
        public int requestsPerMinute = 60;

        @Comment("Tek bir isteğin zaman aşımı (saniye)")
        public int timeoutSeconds = 45;

        @Comment("Geçici hatalarda en çok yeniden deneme sayısı (sonsuz yeniden deneme yoktur)")
        public int maxRetries = 2;

        @Comment("Bekleyen çeviri kuyruğunun üst sınırı (değişiklik için yeniden başlatma gerekir)")
        public int queueSize = 512;

        @Comment("Kalıcı önbellekteki en çok kayıt sayısı")
        public int cacheMaxEntries = 50000;

        @Comment("Model sıcaklığı (düşük değer tutarlı çeviri verir)")
        public double temperature = 0.2;
    }

    @Comment("屏幕翻译排除原版屏幕")
    @ConfigEntry.Gui.Tooltip
    public boolean ignoreOriginalScreen = false;

    @Comment("是否在翻译后的文本里增加原文显示")
    public boolean appendOriginal = true;

    @Comment("无需翻译文本, 支持正则, 不区分大小写")
    public List<String> wordBlacklist = new ArrayList<>();

    @Comment("开启 DEBUG 模式，开启可能会有日志刷屏")
    public boolean debug = false;

    @Comment("忽略的命名空间, 支持正则")
    @ConfigEntry.Gui.Excluded
    public Set<String> excludedNamespace = new HashSet<>() {{
        add("minecraft");
        add("^fabric-.*");
        add("forge");
    }};

    @Comment("翻译图标配置")
    @ConfigEntry.Gui.NoTooltip
    @ConfigEntry.Gui.TransitiveObject
    @ConfigEntry.Category("icon")
    @ConfigEntry.Gui.PrefixText
    public Icon icon = new Icon();

    public static class Icon {

        @Comment("是否常显图标")
        public boolean alwaysDisplay = false;

        @Comment("显示位置")
        public ScreenArea displayArea = ScreenArea.TOP_RIGHT;

        @Comment("X 轴偏移量")
        public int offsetX = 0;

        @Comment("Y 轴偏移量")
        public int offsetY = 0;
    }

    @Comment("Google 翻译相关配置")
    @ConfigEntry.Gui.NoTooltip
    @ConfigEntry.Gui.TransitiveObject
    @ConfigEntry.Category("google")
    @ConfigEntry.Gui.PrefixText
    public Google google = new Google();

    public static class Google {

        @Comment("Google 翻译备用域名，可以填镜像站，只要 API 跟谷歌相同就行")
        @ConfigEntry.Gui.RequiresRestart
        public String domain = "translate.google.com";

        @Comment("Google 服务器 IP，如果您所在地区无法直连域名，可以配置此项\n 参考 https://github.com/Ponderfly/GoogleTranslateIpCheck")
        @ConfigEntry.Gui.Excluded
        public Set<String> dns = new HashSet<>() {{
            add("64.233.189.191");
            add("108.177.97.100");
            add("216.239.32.40");
            add("74.125.196.113");
            add("142.251.171.90");
            add("142.250.1.90");
            add("172.217.218.90");
            add("108.177.126.90");
            add("142.251.1.90");
        }};
    }

    public static void init() {
        AutoConfig.register(Config.class, JanksonConfigSerializer::new);

        AutoTranslation.CONFIG = AutoConfig.getConfigHolder(Config.class).getConfig();
        AutoTranslation.CONFIG.google.domain = AutoTranslation.CONFIG.google.domain.toLowerCase();
        if (AutoTranslation.CONFIG.google.domain.startsWith("http")) {
            try {
                AutoTranslation.CONFIG.google.domain = AutoTranslation.CONFIG.google.domain.split("//")[1];
            } catch (Throwable e) {
                AutoTranslation.LOGGER.warn("Google 备用域名格式错误");
            }
        }
    }
}
