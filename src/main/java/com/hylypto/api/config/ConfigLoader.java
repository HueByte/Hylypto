package com.hylypto.api.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigLoader {

    private final Gson gson;
    private final Path configDir;

    public ConfigLoader(Path dataDirectory) {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.configDir = dataDirectory;
    }

    public <T> T load(String filename, Class<T> type) {
        Path file = configDir.resolve(filename);
        if (!Files.exists(file)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            return gson.fromJson(reader, type);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config: " + filename, e);
        }
    }

    public <T> T loadOrDefault(String filename, Class<T> type, T defaultValue) {
        T loaded = load(filename, type);
        if (loaded == null) {
            save(filename, defaultValue);
            return defaultValue;
        }
        return loaded;
    }

    public <T> void save(String filename, T value) {
        try {
            Files.createDirectories(configDir);
            Path file = configDir.resolve(filename);
            try (Writer writer = Files.newBufferedWriter(file)) {
                gson.toJson(value, writer);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to save config: " + filename, e);
        }
    }

    public Path getConfigDir() {
        return configDir;
    }

    public Gson getGson() {
        return gson;
    }
}
