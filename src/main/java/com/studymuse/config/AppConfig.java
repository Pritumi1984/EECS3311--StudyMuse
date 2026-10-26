package com.studymuse.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Application-wide settings (LLM provider, API key, model, data folder).
 *
 * Design pattern: SINGLETON. The settings are read once from the local ".env" file and
 * shared by every class that needs them, so they can never disagree. Real API keys live
 * only in ".env", which is excluded from Git by .gitignore.
 *
 * Lookup order for each key: environment variable, then .env file, then the default.
 */
public final class AppConfig {

    private static AppConfig instance;

    private final Map<String, String> values = new HashMap<>();
    private final boolean envFileFound;

    private AppConfig(Path envFile) {
        boolean found = false;
        if (Files.isRegularFile(envFile)) {
            found = true;
            try {
                List<String> lines = Files.readAllLines(envFile, StandardCharsets.UTF_8);
                for (String raw : lines) {
                    String line = raw.trim();
                    if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                        continue;
                    }
                    int eq = line.indexOf('=');
                    String key = line.substring(0, eq).trim();
                    String value = line.substring(eq + 1).trim();
                    if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                            || value.startsWith("'") && value.endsWith("'"))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    values.put(key, value);
                }
            } catch (IOException e) {
                System.err.println("Warning: could not read " + envFile + ": " + e.getMessage());
            }
        }
        this.envFileFound = found;
    }

    /** Returns the single shared configuration, loading ".env" from the working folder on first use. */
    public static synchronized AppConfig getInstance() {
        if (instance == null) {
            instance = new AppConfig(Paths.get(".env"));
        }
        return instance;
    }

    /** Test hook: load the configuration from a specific file instead of ./.env. */
    public static synchronized AppConfig loadFrom(Path envFile) {
        instance = new AppConfig(envFile);
        return instance;
    }

    public String get(String key) {
        return get(key, null);
    }

    public String get(String key, String defaultValue) {
        String fromEnv = System.getenv(key);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        String fromFile = values.get(key);
        if (fromFile != null && !fromFile.isBlank()) {
            return fromFile;
        }
        return defaultValue;
    }

    public String getProviderName() {
        return get("LLM_PROVIDER", "gemini").toLowerCase();
    }

    /** The API key, or null when it is missing or still the placeholder from .env.example. */
    public String getApiKey() {
        String key = get("LLM_API_KEY");
        if (key == null || key.startsWith("YOUR_")) {
            return null;
        }
        return key;
    }

    public String getModel(String defaultModel) {
        return get("LLM_MODEL", defaultModel);
    }

    public Path getDataDir() {
        return Paths.get(get("DATA_DIR", "data"));
    }

    public boolean isEnvFileFound() {
        return envFileFound;
    }
}
