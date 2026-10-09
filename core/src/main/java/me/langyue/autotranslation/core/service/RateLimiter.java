package me.langyue.autotranslation.core.service;

import java.util.function.LongSupplier;

/**
 * Spreads provider calls evenly so that at most {@code requestsPerMinute} calls start per minute.
 */
final class RateLimiter {
    private final LongSupplier clock;
    private final Sleeper sleeper;
    private long nextFree = 0L;

    RateLimiter(LongSupplier clock, Sleeper sleeper) {
        this.clock = clock;
        this.sleeper = sleeper;
    }

    /** Blocks (on a worker thread) until a call may start. */
    void acquire(int requestsPerMinute) throws InterruptedException {
        long wait = reserve(requestsPerMinute);
        if (wait > 0) sleeper.sleep(wait);
    }

    synchronized long reserve(int requestsPerMinute) {
        long interval = 60_000L / Math.max(1, requestsPerMinute);
        long now = clock.getAsLong();
        long start = Math.max(now, nextFree);
        nextFree = start + interval;
        return start - now;
    }
}
