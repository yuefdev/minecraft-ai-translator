package me.langyue.autotranslation.core.provider;

import me.langyue.autotranslation.core.lang.LanguageProfile;

import java.util.List;

/**
 * One call to a provider: several already masked segments that belong together.
 *
 * @param strict set on a second attempt after the first answer failed validation
 */
public record ProviderRequest(
        LanguageProfile target,
        List<String> segments,
        TranslationContext context,
        boolean strict
) {
}
