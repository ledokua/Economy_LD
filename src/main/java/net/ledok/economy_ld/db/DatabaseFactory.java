package net.ledok.economy_ld.db;

import net.ledok.economy_ld.config.EconomyConfig;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;

public final class DatabaseFactory {
    private DatabaseFactory() {
    }

    public static EconomyDatabase create(EconomyConfig config, Path configDirectory, ExecutorService dbExecutor) {
        if ("mysql".equalsIgnoreCase(config.storageType)) {
            return new MySqlEconomyDatabase(config, dbExecutor);
        }
        return new SqliteEconomyDatabase(configDirectory.resolve(config.sqlite.file), dbExecutor);
    }
}
