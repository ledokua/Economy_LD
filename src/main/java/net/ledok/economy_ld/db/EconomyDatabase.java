package net.ledok.economy_ld.db;

import java.util.UUID;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;

public interface EconomyDatabase {
    CompletableFuture<Void> initialize();

    CompletableFuture<Void> shutdown();

    CompletableFuture<Long> getBalance(UUID uuid, String username);

    CompletableFuture<OptionalLong> getBalanceByUsername(String username);

    CompletableFuture<Void> setBalance(UUID uuid, String username, long balance);

    CompletableFuture<Void> addBalance(UUID uuid, String username, long delta);

    CompletableFuture<Boolean> transfer(
            UUID fromUuid,
            String fromUsername,
            UUID toUuid,
            String toUsername,
            long amount
    );
}
