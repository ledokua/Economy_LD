package net.ledok.economy_ld.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigLoader {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "economy_ld.json";

    private ConfigLoader() {
    }

    public static EconomyConfig load(Logger logger) {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        try {
            if (Files.notExists(configPath)) {
                EconomyConfig defaults = new EconomyConfig();
                defaults.sanitize();
                save(configPath, defaults);
                logger.info("Created default config at {}", configPath);
                return defaults;
            }

            String json = Files.readString(configPath);
            EconomyConfig loaded = GSON.fromJson(json, EconomyConfig.class);
            if (loaded == null) {
                loaded = new EconomyConfig();
            }
            loaded.sanitize();
            save(configPath, loaded);
            return loaded;
        } catch (Exception e) {
            logger.error("Failed to load config at {}. Falling back to defaults.", configPath, e);
            EconomyConfig fallback = new EconomyConfig();
            fallback.sanitize();
            return fallback;
        }
    }

    private static void save(Path path, EconomyConfig config) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, GSON.toJson(config));
    }
}
