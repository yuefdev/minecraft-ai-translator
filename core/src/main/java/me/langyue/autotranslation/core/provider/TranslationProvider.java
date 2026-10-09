package me.langyue.autotranslation.core.provider;

import java.util.List;

/**
 * A translation backend. Implementations must be thread safe, must honour their own network timeouts and must
 * never log or expose credentials.
 */
public interface TranslationProvider {

    /** Stable identifier that is part of every cache key (include the endpoint host so switching invalidates). */
    String id();

    /** Model / engine name; part of every cache key. */
    String model();

    /** Whether the provider has everything it needs (endpoint, model, key) to be called at all. */
    default boolean isConfigured() {
        return true;
    }

    /**
     * @return exactly one translation per request segment, in order
     */
    List<String> translate(ProviderRequest request) throws ProviderException;
}
