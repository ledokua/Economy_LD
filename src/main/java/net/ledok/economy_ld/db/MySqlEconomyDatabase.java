package net.ledok.economy_ld.db;

import net.ledok.economy_ld.config.EconomyConfig;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class MySqlEconomyDatabase implements EconomyDatabase {
    private final ExecutorService dbExecutor;

    public MySqlEconomyDatabase(EconomyConfig config, ExecutorService dbExecutor) {
        this.dbExecutor = dbExecutor;
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("MySQL backend is not implemented yet."));
    }

    @Override
    public CompletableFuture<Void> close() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Long> getBalance(UUID playerUuid, String username) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("MySQL backend is not implemented yet."));
    }

    @Override
    public CompletableFuture<Void> setBalance(UUID playerUuid, String username, long balance) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("MySQL backend is not implemented yet."));
    }

    @Override
    public CompletableFuture<Boolean> transfer(UUID fromUuid, String fromUsername, UUID toUuid, String toUsername, long amount) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("MySQL backend is not implemented yet."));
    }
}
