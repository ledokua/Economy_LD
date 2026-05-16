package net.ledok.economy_ld.db;

import net.ledok.economy_ld.auction.AuctionRecord;
import net.ledok.economy_ld.auction.PendingDelivery;
import net.ledok.economy_ld.shop.ShopRecord;
import net.ledok.economy_ld.shop.ShopListing;
import net.minecraft.core.RegistryAccess;
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

    CompletableFuture<Boolean> placeAuction(
            UUID sellerUuid,
            String sellerName,
            ItemStack item,
            int quantity,
            long startPrice,
            Long buyoutPrice,
            long expiresAt,
            int listingFeePercent,
            int maxListings,
            RegistryAccess registryAccess
    );

    CompletableFuture<Boolean> placeBid(UUID auctionId, UUID bidderUuid, String bidderName, long bidAmount);

    CompletableFuture<Boolean> buyout(UUID auctionId, UUID buyerUuid, String buyerName, int serverTaxPercent);

    CompletableFuture<Boolean> cancelAuction(UUID auctionId, UUID requesterUuid);

    CompletableFuture<List<AuctionRecord>> getActiveAuctions(RegistryAccess registryAccess);

    CompletableFuture<List<AuctionRecord>> getPlayerAuctions(UUID sellerUuid, RegistryAccess registryAccess);

    CompletableFuture<List<AuctionRecord>> getPlayerBids(UUID bidderUuid, RegistryAccess registryAccess);

    CompletableFuture<List<UUID>> processExpiredAuctions(int serverTaxPercent, RegistryAccess registryAccess);

    CompletableFuture<List<PendingDelivery>> claimPendingDeliveries(UUID playerUuid);

    CompletableFuture<List<PendingDelivery>> getPendingDeliveries(UUID playerUuid);

    CompletableFuture<Optional<PendingDelivery>> claimSingleDelivery(long deliveryId);

    CompletableFuture<List<PendingDelivery>> claimAllDeliveries(UUID playerUuid);

    CompletableFuture<Void> cleanExpiredDeliveries();

    CompletableFuture<Integer> getEffectiveListingLimit(UUID playerUuid, int defaultLimit);

    CompletableFuture<Void> setAuctionBonusLimit(UUID playerUuid, int bonus);

    CompletableFuture<Integer> adjustAuctionBonusLimit(UUID playerUuid, int delta);
}
