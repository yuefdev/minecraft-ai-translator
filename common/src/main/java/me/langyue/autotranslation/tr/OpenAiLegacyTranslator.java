package me.langyue.autotranslation.tr;

import me.langyue.autotranslation.core.lang.LanguageProfile;
import me.langyue.autotranslation.core.provider.ProviderException;
import me.langyue.autotranslation.core.provider.ProviderRequest;
import me.langyue.autotranslation.core.provider.TranslationContext;
import me.langyue.autotranslation.translate.ITranslator;

import java.util.List;

/**
 * Adapter so that the upstream bulk language-file translation ({@code ResourceManager}) can also use the
 * OpenAI compatible provider when the user opts in to it. Everything else uses {@link TranslationBridge}.
 */
public final class OpenAiLegacyTranslator implements ITranslator {

    @Override
    public void init() {
    }

    @Override
    public boolean ready() {
        return TranslationRuntime.providerConfigured();
    }

    @Override
    public int maxLength() {
        return 3000;
    }

    @Override
    public String translate(String text, String tl, String sl) {
        if (!TranslationRuntime.providerConfigured()) return null;
        try {
            LanguageProfile target = LanguageProfile.of(tl);
            TranslationContext context = new TranslationContext("", "language file", "", "", "", java.util.Map.of());
            List<String> out = TranslationRuntime.provider().translate(new ProviderRequest(target, List.of(text), context, false));
            return out.isEmpty() ? null : out.get(0);
        } catch (ProviderException e) {
            me.langyue.autotranslation.AutoTranslation.LOGGER.warn("OpenAI translation failed: {}", e.getMessage());
            return null;
        }
    }
}
