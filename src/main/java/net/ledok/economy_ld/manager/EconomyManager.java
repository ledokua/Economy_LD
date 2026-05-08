package net.ledok.economy_ld.manager;

import net.fabricmc.loader.api.FabricLoader;
import net.ledok.economy_ld.config.EconomyConfig;
import net.ledok.economy_ld.db.DatabaseFactory;
import net.ledok.economy_ld.db.EconomyDatabase;
import org.slf4j.Logger;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class EconomyManager {
    private static final EconomyManager INSTANCE = new EconomyManager();

    private ExecutorService dbExecutor;
    private EconomyDatabase database;

    private EconomyManager() {
    }

    public static EconomyManager getInstance() {
        return INSTANCE;
    }

    public synchronized void start(EconomyConfig config, Logger logger) {
        if (database != null) {
            return;
        }

        dbExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "economy-ld-db");
            thread.setDaemon(true);
            return thread;
        });
        database = DatabaseFactory.create(config, FabricLoader.getInstance().getConfigDir(), dbExecutor);
        database.initialize().whenComplete((unused, throwable) -> {
            if (throwable == null) {
                logger.info("Economy database initialized with storage type '{}'.", config.storageType);
            } else {
                logger.error("Failed to initialize economy database.", throwable);
            }
        });
    }

    public synchronized void stop() {
        if (database == null) {
            return;
        }
        database.close();
        dbExecutor.shutdown();
        database = null;
        dbExecutor = null;
    }

    public CompletableFuture<Long> getBalance(UUID uuid, String username) {
        return database.getBalance(uuid, username);
    }

    public CompletableFuture<Void> setBalance(UUID uuid, String username, long newBalance) {
        if (newBalance < 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Balance cannot be negative."));
        }
        return database.setBalance(uuid, username, newBalance);
    }

    public CompletableFuture<Boolean> transfer(UUID fromUuid, String fromUsername, UUID toUuid, String toUsername, long amount) {
        if (amount <= 0) {
            return CompletableFuture.completedFuture(false);
        }
        return database.transfer(fromUuid, fromUsername, toUuid, toUsername, amount);
    }
}
