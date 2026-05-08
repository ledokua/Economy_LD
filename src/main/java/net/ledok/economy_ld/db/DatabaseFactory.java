package net.ledok.economy_ld.db;

import net.fabricmc.loader.api.FabricLoader;
import net.ledok.economy_ld.config.EconomyConfig;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;

public final class DatabaseFactory {
    private DatabaseFactory() {
    }

    public static EconomyDatabase create(EconomyConfig config, ExecutorService executor) {
        if ("mariadb".equalsIgnoreCase(config.storageType)) {
            EconomyConfig.Mariadb maria = config.mariadb;
            return new MariaDbEconomyDatabase(
                    maria.host,
                    maria.port,
                    maria.database,
                    maria.username,
                    maria.password,
                    executor
            );
        }

        Path sqlitePath = FabricLoader.getInstance().getGameDir().resolve(config.sqlite.file);
        return new SqliteEconomyDatabase(sqlitePath, executor);
    }
}
