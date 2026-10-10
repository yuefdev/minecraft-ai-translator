package me.langyue.autotranslation.engine;

import me.langyue.autotranslation.core.mask.MaskProfile;
import me.langyue.autotranslation.core.service.TranslationService;
import me.langyue.autotranslation.core.service.Types.Content;
import me.langyue.autotranslation.core.service.Types.Result;
import me.langyue.autotranslation.core.service.Types.Segment;
import me.langyue.autotranslation.core.service.Types.State;
import me.langyue.autotranslation.core.service.Types.Stats;
import me.langyue.autotranslation.core.service.Types.TextRequest;
import me.langyue.autotranslation.core.util.Compat;
import me.langyue.autotranslation.core.util.CoreLog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Translates the language files of all mods in the background, so that a text is already Turkish the first time it is
 * shown instead of English for a few seconds (or for good when the queue was full). The texts go through the same
 * lookup, cache and scope ({@code lang:<key>}) as a text that is shown on screen, so whatever this finds is simply
 * there when the game asks for it; nothing is translated twice.
 *
 * <p>It is polite: it keeps only a small batch waiting in the queue, so texts the player is looking at right now
 * are never stuck behind thousands of others, and it stops while the service is paused (quota, block). A text that
 * could not be translated is tried again the next time the game starts.
 */
final class Pretranslator implements Runnable {
    /** Jobs waiting in the queue: about one provider batch, so a text from the screen waits for one call at most. */
    private static final int QUEUE_TARGET = 40;
    private static final int STEP = 20;
    private static final long IDLE_MILLIS = 3000L;
    private static final long RETRY_MILLIS = 30_000L;
    private static final long BUMP_MILLIS = 1500L;
    private static final long LOG_MILLIS = 60_000L;

    private final Engine engine;
    private final LanguageSource source;
    private volatile boolean stopped;
    private Thread thread;

    Pretranslator(Engine engine, LanguageSource source) {
        this.engine = engine;
        this.source = source;
    }

    void start() {
        thread = new Thread(this, "AutoTranslation-Pretranslate");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    void stop() {
        stopped = true;
        if (thread != null) thread.interrupt();
    }

    @Override
    public void run() {
        String finished = null;
        try {
            while (!stopped) {
                if (!wanted()) {
                    pause(IDLE_MILLIS);
                    continue;
                }
                String language = engine.languageCode();
                if (language == null || language.equals(finished)) {
                    pause(IDLE_MILLIS);
                    continue;
                }
                List<Map.Entry<String, String>> entries;
                try {
                    entries = new ArrayList<Map.Entry<String, String>>(source.untranslated(language).entrySet());
                } catch (IOException e) {
                    CoreLog.warn("Pre-translation: the language files cannot be read yet (" + e.getMessage() + "); trying again later");
                    pause(RETRY_MILLIS);
                    continue;
                } catch (RuntimeException e) {
                    CoreLog.warn("Pre-translation: reading the language files failed; giving up until the next start", e);
                    return;
                }
                if (feed(language, entries)) finished = language;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            CoreLog.warn("Pre-translation stopped by an unexpected error", e);
        }
    }

    private boolean wanted() {
        return engine.settings().pretranslateLanguageFiles && engine.genericEnabled();
    }

    /** @return true if every text was handed to the service, false if the language or the settings changed meanwhile */
    private boolean feed(String language, List<Map.Entry<String, String>> entries) throws InterruptedException {
        TranslationService service = engine.service();
        int total = entries.size();
        CoreLog.info("Pre-translation: " + total + " texts of the mods' language files have no " + language
                + " version; translating them in the background (the results are cached for the next start)");
        int next = 0;
        int resolved = 0;
        long lastBump = 0L;
        long lastLog = System.currentTimeMillis();
        long lastTranslated = service.stats().translatedSegments();
        boolean refusedBefore = false;
        while (next < total) {
            if (stopped || !wanted() || !language.equals(engine.languageCode())) return false;
            long now = System.currentTimeMillis();
            Stats stats = service.stats();
            if (stats.translatedSegments() != lastTranslated && now - lastBump >= BUMP_MILLIS) {
                lastTranslated = stats.translatedSegments();
                lastBump = now;
                engine.invalidateDisplays();
            }
            if (now - lastLog >= LOG_MILLIS) {
                lastLog = now;
                CoreLog.info("Pre-translation: " + next + " of " + total + " texts handled");
            }
            if (!service.providerConfigured()) {
                pause(10_000L);
                continue;
            }
            if (stats.pausedUntilMillis() > now) {
                pause(Math.min(10_000L, stats.pausedUntilMillis() - now));
                continue;
            }
            if (stats.queued() >= QUEUE_TARGET) {
                pause(200L);
                continue;
            }
            int sent = 0;
            boolean refused = false;
            while (next < total && sent < STEP) {
                Map.Entry<String, String> entry = entries.get(next);
                Result result = service.lookup(request(entry.getKey(), entry.getValue()), null);
                if (result.state() == State.FAILED) {
                    refused = true;
                    break;
                }
                next++;
                if (result.state() == State.PENDING) {
                    sent++;
                } else {
                    resolved++;
                }
            }
            if (refused) {
                if (!refusedBefore) CoreLog.debug("Pre-translation: the service refused a text, waiting");
                refusedBefore = true;
                pause(2000L);
            } else if (sent == 0) {
                pause(1L); // a long run of texts that need nothing: let the other threads breathe
            }
        }
        // let the last texts finish so the screen can show them
        long until = System.currentTimeMillis() + 120_000L;
        while (!stopped && System.currentTimeMillis() < until) {
            Stats stats = service.stats();
            if (stats.queued() == 0 && stats.inFlight() == 0) break;
            pause(500L);
        }
        engine.invalidateDisplays();
        Stats stats = service.stats();
        CoreLog.info("Pre-translation finished: " + total + " texts handled (" + resolved + " needed no request), "
                + stats.translatedSegments() + " translated since start, " + stats.failedSegments() + " failed");
        return true;
    }

    private static TextRequest request(String key, String english) {
        Content content = new Content("game", "language file", "lang:" + key, "");
        return new TextRequest(Compat.listOf(Segment.plain(english)), content, MaskProfile.PLAIN, "pre:" + key);
    }

    private static void pause(long millis) throws InterruptedException {
        Thread.sleep(Math.max(1L, millis));
    }
}
