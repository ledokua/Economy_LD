package net.ledok.economy_ld.db;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface EconomyDatabase {
    CompletableFuture<Void> initialize();

    CompletableFuture<Void> close();

    CompletableFuture<Long> getBalance(UUID playerUuid, String username);

    CompletableFuture<Void> setBalance(UUID playerUuid, String username, long balance);

    CompletableFuture<Boolean> transfer(UUID fromUuid, String fromUsername, UUID toUuid, String toUsername, long amount);
}
