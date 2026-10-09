package me.langyue.autotranslation.core;

import me.langyue.autotranslation.core.util.CoreLog;
import me.langyue.autotranslation.core.util.Redactor;
import me.langyue.autotranslation.core.util.Secret;
import me.langyue.autotranslation.core.util.SecretStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class SecretStoreTest {
    @TempDir
    Path dir;

    @AfterEach
    void reset() {
        Redactor.clearRegistered();
    }

    @Test
    void keyIsStoredInItsOwnFileAndRoundTrips() throws Exception {
        SecretStore store = new SecretStore(dir.resolve("secrets/api-key.txt"));
        assertTrue(store.load().isEmpty());
        store.save(Secret.of("  sk-abc-123456789  "));
        assertEquals("sk-abc-123456789", store.load().reveal());
        store.save(Secret.empty());
        assertFalse(Files.exists(dir.resolve("secrets/api-key.txt")));
    }

    @Test
    void savedKeyIsOwnerOnlyWhereSupported() throws Exception {
        Path file = dir.resolve("secrets/api-key.txt");
        new SecretStore(file).save(Secret.of("sk-abc-123456789"));
        try {
            var perms = Files.getPosixFilePermissions(file);
            assertFalse(perms.contains(java.nio.file.attribute.PosixFilePermission.GROUP_READ));
            assertFalse(perms.contains(java.nio.file.attribute.PosixFilePermission.OTHERS_READ));
        } catch (UnsupportedOperationException ignored) {
            // non-POSIX file system
        }
    }

    @Test
    void loadedAndSavedKeysAreRegisteredForRedaction() throws Exception {
        Path file = dir.resolve("api-key.txt");
        new SecretStore(file).save(Secret.of("topsecretvalue-0001"));
        Redactor.clearRegistered();
        new SecretStore(file).load();
        assertFalse(Redactor.redact("failed with topsecretvalue-0001 in header").contains("topsecretvalue-0001"));
    }

    @Test
    void environmentVariableWinsOverTheFile() throws Exception {
        Path file = dir.resolve("api-key.txt");
        new SecretStore(file).save(Secret.of("file-key-000000"));
        var field = SecretStore.class.getDeclaredConstructor(Path.class, java.util.function.Function.class);
        field.setAccessible(true);
        SecretStore withEnv = field.newInstance(file, (java.util.function.Function<String, String>) name -> "env-key-111111");
        assertTrue(withEnv.isFromEnvironment());
        assertEquals("env-key-111111", withEnv.load().reveal());
    }

    @Test
    void nothingSecretIsEverPrintedByTheLoggerEvenAfterAFailure() throws Exception {
        List<String> lines = new CopyOnWriteArrayList<>();
        CoreLog.setSink((level, message, error) -> {
            lines.add(message);
            if (error != null) lines.add(error.getMessage() + "");
        });
        try {
            new SecretStore(dir.resolve("k.txt")).save(Secret.of("leaky-key-987654"));
            CoreLog.error("call failed: leaky-key-987654", new RuntimeException("body: leaky-key-987654"));
        } finally {
            CoreLog.setSink((l, m, e) -> {
            });
        }
        assertFalse(String.join("\n", lines).contains("leaky-key-987654"));
    }
}
