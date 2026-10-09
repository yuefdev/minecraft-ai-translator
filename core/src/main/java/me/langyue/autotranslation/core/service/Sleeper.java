package me.langyue.autotranslation.core.service;

/** Indirection over {@link Thread#sleep} so that retry/rate-limit timing is testable without real waiting. */
@FunctionalInterface
public interface Sleeper {
    Sleeper SYSTEM = Thread::sleep;

    void sleep(long millis) throws InterruptedException;
}
