package me.langyue.autotranslation.core.provider;

import me.langyue.autotranslation.core.lang.LanguageProfile;

import java.util.List;

/**
 * One call to a provider: several already masked segments that belong together.
 *
 * @param strict set on a second attempt after the first answer failed validation
 */
public final class ProviderRequest {
    private final LanguageProfile target;
    private final List<String> segments;
    private final TranslationContext context;
    private final boolean strict;

    public ProviderRequest(LanguageProfile target, List<String> segments, TranslationContext context, boolean strict) {
        this.target = target;
        this.segments = segments;
        this.context = context;
        this.strict = strict;
    }

    public LanguageProfile target() {
        return target;
    }

    public List<String> segments() {
        return segments;
    }

    public TranslationContext context() {
        return context;
    }

    public boolean strict() {
        return strict;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProviderRequest other = (ProviderRequest) o;
        return java.util.Objects.equals(target, other.target) && java.util.Objects.equals(segments, other.segments) && java.util.Objects.equals(context, other.context) && strict == other.strict;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(target, segments, context, strict);
    }

    @Override
    public String toString() {
        return "ProviderRequest[" + "target=" + target + ", " + "segments=" + segments + ", " + "context=" + context + ", " + "strict=" + strict + "]";
    }
}
