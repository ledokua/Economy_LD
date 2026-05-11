package net.ledok.economy_ld.db;

import net.ledok.economy_ld.shop.ShopRecord;
import net.ledok.economy_ld.shop.ShopListing;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface EconomyDatabase {
    CompletableFuture<Void> initialize();

    CompletableFuture<Void> shutdown();

    CompletableFuture<Long> getBalance(UUID uuid, String username);

    CompletableFuture<OptionalLong> getBalanceByUsername(String username);

    CompletableFuture<Optional<UUID>> getUuidByUsername(String username);

    CompletableFuture<Optional<String>> getUsernameByUuid(UUID uuid);

    CompletableFuture<List<String>> getAllUsernames();

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

    CompletableFuture<Void> addListing(UUID shopId, ItemStack item, Long priceBuy, Long priceSell, int perOp, Long buyCap);

    CompletableFuture<List<ShopListing>> getListings(UUID shopId);

    CompletableFuture<Void> removeListing(UUID listingId);

    CompletableFuture<Void> updateListing(UUID listingId, Long priceBuy, Long priceSell, int perOp, Long buyCap);

    CompletableFuture<Boolean> restockListing(UUID listingId, int quantity);

    CompletableFuture<Boolean> buyItem(UUID listingId, UUID buyerUuid, String buyerUsername, int quantity);

    CompletableFuture<Boolean> sellItem(UUID listingId, UUID sellerUuid, String sellerUsername, int quantity);
}
