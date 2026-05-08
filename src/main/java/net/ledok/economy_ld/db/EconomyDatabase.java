package net.ledok.economy_ld.db;

import net.ledok.economy_ld.shop.ShopRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;

public interface EconomyDatabase {
    CompletableFuture<Void> initialize();

    CompletableFuture<Void> shutdown();

    CompletableFuture<Long> getBalance(UUID uuid, String username);

    CompletableFuture<OptionalLong> getBalanceByUsername(String username);

    CompletableFuture<Optional<UUID>> getUuidByUsername(String username);

    CompletableFuture<Void> setBalance(UUID uuid, String username, long balance);

    CompletableFuture<Void> addBalance(UUID uuid, String username, long delta);

    CompletableFuture<Boolean> transfer(
            UUID fromUuid,
            String fromUsername,
            UUID toUuid,
            String toUsername,
            long amount
    );

    CompletableFuture<Void> upsertShop(
            UUID shopId,
            UUID ownerUuid,
            boolean isAdmin,
            ResourceLocation dimension,
            BlockPos pos
    );

    CompletableFuture<Optional<ShopRecord>> getShop(UUID shopId);

    CompletableFuture<Void> deleteShop(UUID shopId);
}
