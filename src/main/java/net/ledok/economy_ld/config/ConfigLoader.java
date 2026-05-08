package net.ledok.economy_ld.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigLoader {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "economy_ld.json";

    private ConfigLoader() {
    }

    public static EconomyConfig load(Logger logger) {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        if (Files.notExists(configPath)) {
            EconomyConfig defaults = new EconomyConfig();
            save(defaults, logger, configPath);
            return defaults;
        }

        try (Reader reader = Files.newBufferedReader(configPath)) {
            EconomyConfig loaded = GSON.fromJson(reader, EconomyConfig.class);
            return loaded != null ? loaded : new EconomyConfig();
        } catch (Exception exception) {
            logger.error("Failed to read {}, using defaults.", configPath, exception);
            return new EconomyConfig();
        }
    }

    private static void save(EconomyConfig config, Logger logger, Path path) {
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException exception) {
            logger.error("Failed to write default config at {}", path, exception);
        }
    }
}
