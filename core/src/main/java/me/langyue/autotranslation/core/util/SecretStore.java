package me.langyue.autotranslation.core.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.function.Function;

/**
 * Where the API key lives. It is deliberately kept out of the normal config file (which people copy around and
 * commit with modpacks): either in the environment variable {@value #ENV_NAME}, which wins, or in a small file
 * inside the translator's private data folder. The file is created readable by the owner only where the
 * platform supports it, and the folder is git-ignored by the project's {@code .gitignore}.
 */
public final class SecretStore {
    public static final String ENV_NAME = "AUTOTRANSLATION_API_KEY";

    private final Path file;
    private final Function<String, String> environment;

    public SecretStore(Path file) {
        this(file, System::getenv);
    }

    SecretStore(Path file, Function<String, String> environment) {
        this.file = file;
        this.environment = environment;
    }

    public Path file() {
        return file;
    }

    /** True if the key comes from the environment (the settings screen then cannot change it). */
    public boolean isFromEnvironment() {
        String value = environment.apply(ENV_NAME);
        return value != null && !Compat.isBlank(value);
    }

    public Secret load() {
        String fromEnv = environment.apply(ENV_NAME);
        if (fromEnv != null && !Compat.isBlank(fromEnv)) {
            return register(Secret.of(fromEnv));
        }
        try {
            if (Files.exists(file)) {
                return register(Secret.of(Compat.readString(file, StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            CoreLog.warn("Could not read the stored API key: " + e.getClass().getSimpleName());
        }
        return Secret.empty();
    }

    public void save(Secret secret) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        if (secret.isEmpty()) {
            Files.deleteIfExists(file);
            return;
        }
        if (!Files.exists(file)) {
            try {
                Files.createFile(file, java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rw-------")));
            } catch (UnsupportedOperationException e) {
                Files.createFile(file); // not a POSIX file system (for example Windows)
            }
        }
        Compat.writeString(file, secret.reveal(), StandardCharsets.UTF_8);
        register(secret);
    }

    private static Secret register(Secret secret) {
        Redactor.register(secret);
        return secret;
    }
}
