package net.ledok.economy_ld.manager;

import net.ledok.economy_ld.config.ConfigLoader;
import net.ledok.economy_ld.config.EconomyConfig;
import net.ledok.economy_ld.db.DatabaseFactory;
import net.ledok.economy_ld.db.EconomyDatabase;
import net.ledok.economy_ld.shop.ShopRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.UUID;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class EconomyManager {
    private static EconomyManager instance;

    private final Logger logger;
    private final ExecutorService dbExecutor;
    private EconomyConfig config;
    private EconomyDatabase database;

    private EconomyManager(Logger logger) {
        this.logger = logger;
        this.dbExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "economy-ld-db");
            thread.setDaemon(true);
            return thread;
        });
    }

    public static synchronized EconomyManager initialize(Logger logger) {
        if (instance == null) {
            instance = new EconomyManager(logger);
            instance.bootstrap();
        }
        return instance;
    }

    public static EconomyManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("EconomyManager is not initialized");
        }
        return instance;
    }

    private void bootstrap() {
        this.config = ConfigLoader.load(logger);
        this.database = DatabaseFactory.create(config, dbExecutor);
        database.initialize().join();
        logger.info("Economy database initialized with storage type '{}'", config.storageType);
    }

    public EconomyConfig getConfig() {
        return config;
    }

    public EconomyDatabase requireDatabase() {
        if (database == null) {
            throw new IllegalStateException("Economy database is not available");
        }
        return database;
    }

    public CompletableFuture<Long> getBalance(UUID uuid, String username) {
        return requireDatabase().getBalance(uuid, username);
    }

    public CompletableFuture<OptionalLong> getBalanceByUsername(String username) {
        return requireDatabase().getBalanceByUsername(username);
    }

    public CompletableFuture<Optional<UUID>> getUuidByUsername(String username) {
        return requireDatabase().getUuidByUsername(username);
    }

    public CompletableFuture<Void> give(UUID uuid, String username, long amount) {
        if (amount <= 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Amount must be greater than 0"));
        }
        return requireDatabase().addBalance(uuid, username, amount);
    }

    public CompletableFuture<Boolean> take(UUID uuid, String username, long amount) {
        if (amount <= 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Amount must be greater than 0"));
        }
        return requireDatabase().getBalance(uuid, username).thenCompose(balance -> {
            if (balance < amount) {
                return CompletableFuture.completedFuture(false);
            }
            return requireDatabase().addBalance(uuid, username, -amount).thenApply(v -> true);
        });
    }

    public CompletableFuture<Void> set(UUID uuid, String username, long balance) {
        if (balance < 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Balance cannot be negative"));
        }
        return requireDatabase().setBalance(uuid, username, balance);
    }

    public CompletableFuture<Boolean> transfer(UUID fromUuid, String fromUsername, UUID toUuid, String toUsername, long amount) {
        if (amount <= 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Amount must be greater than 0"));
        }
        if (fromUuid.equals(toUuid)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Cannot transfer to self"));
        }
        return requireDatabase().transfer(fromUuid, fromUsername, toUuid, toUsername, amount);
    }

    public CompletableFuture<Void> upsertShop(
            UUID shopId,
            UUID ownerUuid,
            boolean isAdmin,
            ResourceLocation dimension,
            BlockPos pos
    ) {
        return requireDatabase().upsertShop(shopId, ownerUuid, isAdmin, dimension, pos);
    }

    public CompletableFuture<Optional<ShopRecord>> getShop(UUID shopId) {
        return requireDatabase().getShop(shopId);
    }

    public CompletableFuture<Void> deleteShop(UUID shopId) {
        return requireDatabase().deleteShop(shopId);
    }

    public CompletableFuture<Void> reloadConfig() {
        return CompletableFuture.runAsync(() -> {
            this.config = ConfigLoader.load(logger);
            logger.info("Economy config reloaded");
        });
    }

    public synchronized void shutdown() {
        if (database != null) {
            try {
                database.shutdown().join();
            } catch (Exception e) {
                logger.error("Failed to shutdown economy database cleanly", e);
            }
            database = null;
        }
        dbExecutor.shutdown();
    }
}
