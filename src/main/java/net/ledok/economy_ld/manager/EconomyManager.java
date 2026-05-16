package net.ledok.economy_ld.manager;

import net.ledok.economy_ld.config.ConfigLoader;
import net.ledok.economy_ld.config.EconomyConfig;
import net.ledok.economy_ld.auction.AuctionRecord;
import net.ledok.economy_ld.auction.PendingDelivery;
import net.ledok.economy_ld.db.DatabaseFactory;
import net.ledok.economy_ld.db.EconomyDatabase;
import net.ledok.economy_ld.shop.ShopListing;
import net.ledok.economy_ld.shop.ShopRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.UUID;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.List;
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class EconomyManager {
    private static EconomyManager instance;

    private final Logger logger;
    private final ExecutorService dbExecutor;
    private final Set<UUID> adminModeActive = Collections.newSetFromMap(new ConcurrentHashMap<>());
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

    public CompletableFuture<Optional<String>> getUsernameByUuid(UUID uuid) {
        return requireDatabase().getUsernameByUuid(uuid);
    }

    public CompletableFuture<List<String>> getAllUsernames() {
        return requireDatabase().getAllUsernames();
    }

    public boolean toggleAdminMode(UUID uuid) {
        if (adminModeActive.contains(uuid)) {
            adminModeActive.remove(uuid);
            return false;
        } else {
            adminModeActive.add(uuid);
            return true;
        }
    }

    public boolean isAdminModeActive(UUID uuid) {
        return adminModeActive.contains(uuid);
    }

    public void clearAdminMode(UUID uuid) {
        adminModeActive.remove(uuid);
    }

    public int getAuctionConfig_listingFeePercent() {
        return this.config.auction.listingFeePercent;
    }

    public int getAuctionConfig_serverTaxPercent() {
        return this.config.auction.serverTaxPercent;
    }

    public int getAuctionConfig_defaultMaxListings() {
        return this.config.auction.defaultMaxListingsPerPlayer;
    }

    public boolean getAuctionConfig_buyoutEnabled() {
        return this.config.auction.buyoutEnabled;
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

    public CompletableFuture<Void> addListing(UUID shopId, ItemStack item, Long priceBuy, Long priceSell, int perOp, Long buyCap) {
        return requireDatabase().addListing(shopId, item, priceBuy, priceSell, perOp, buyCap);
    }

    public CompletableFuture<List<ShopListing>> getListings(UUID shopId) {
        return requireDatabase().getListings(shopId);
    }

    public CompletableFuture<Void> removeListing(UUID listingId) {
        return requireDatabase().removeListing(listingId);
    }

    public CompletableFuture<Void> updateListing(UUID listingId, Long priceBuy, Long priceSell, int perOp, Long buyCap) {
        return requireDatabase().updateListing(listingId, priceBuy, priceSell, perOp, buyCap);
    }

    public CompletableFuture<Boolean> restockListing(UUID listingId, int quantity) {
        if (quantity <= 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Quantity must be greater than 0"));
        }
        return requireDatabase().restockListing(listingId, quantity);
    }

    public CompletableFuture<Boolean> buyItem(UUID listingId, UUID buyerUuid, String buyerUsername, int quantity) {
        if (quantity <= 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Quantity must be greater than 0"));
        }
        return requireDatabase().buyItem(listingId, buyerUuid, buyerUsername, quantity);
    }

    public CompletableFuture<Boolean> sellItem(UUID listingId, UUID sellerUuid, String sellerUsername, int quantity) {
        if (quantity <= 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Quantity must be greater than 0"));
        }
        return requireDatabase().sellItem(listingId, sellerUuid, sellerUsername, quantity);
    }

    public CompletableFuture<Boolean> placeAuction(
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
    ) {
        return requireDatabase().placeAuction(
                sellerUuid,
                sellerName,
                item,
                quantity,
                startPrice,
                buyoutPrice,
                expiresAt,
                listingFeePercent,
                maxListings,
                registryAccess
        );
    }

    public CompletableFuture<Boolean> placeBid(UUID auctionId, UUID bidderUuid, String bidderName, long bidAmount) {
        return requireDatabase().placeBid(auctionId, bidderUuid, bidderName, bidAmount);
    }

    public CompletableFuture<Boolean> buyout(UUID auctionId, UUID buyerUuid, String buyerName, int serverTaxPercent) {
        return requireDatabase().buyout(auctionId, buyerUuid, buyerName, serverTaxPercent);
    }

    public CompletableFuture<Boolean> cancelAuction(UUID auctionId, UUID requesterUuid) {
        return requireDatabase().cancelAuction(auctionId, requesterUuid);
    }

    public CompletableFuture<List<AuctionRecord>> getActiveAuctions(RegistryAccess registryAccess) {
        return requireDatabase().getActiveAuctions(registryAccess);
    }

    public CompletableFuture<List<AuctionRecord>> getPlayerAuctions(UUID sellerUuid, RegistryAccess registryAccess) {
        return requireDatabase().getPlayerAuctions(sellerUuid, registryAccess);
    }

    public CompletableFuture<List<AuctionRecord>> getPlayerBids(UUID bidderUuid, RegistryAccess registryAccess) {
        return requireDatabase().getPlayerBids(bidderUuid, registryAccess);
    }

    public CompletableFuture<List<UUID>> processExpiredAuctions(int serverTaxPercent, RegistryAccess registryAccess) {
        return requireDatabase().processExpiredAuctions(serverTaxPercent, registryAccess);
    }

    public CompletableFuture<List<PendingDelivery>> getPendingDeliveries(UUID playerUuid) {
        return requireDatabase().getPendingDeliveries(playerUuid);
    }

    public CompletableFuture<Optional<PendingDelivery>> claimSingleDelivery(long deliveryId) {
        return requireDatabase().claimSingleDelivery(deliveryId);
    }

    public CompletableFuture<List<PendingDelivery>> claimAllDeliveries(UUID playerUuid) {
        return requireDatabase().claimAllDeliveries(playerUuid);
    }

    public CompletableFuture<Void> cleanExpiredDeliveries() {
        return requireDatabase().cleanExpiredDeliveries();
    }

    public CompletableFuture<Integer> getEffectiveListingLimit(UUID playerUuid, int defaultLimit) {
        return requireDatabase().getEffectiveListingLimit(playerUuid, defaultLimit);
    }

    public CompletableFuture<Void> setAuctionBonusLimit(UUID playerUuid, int bonus) {
        return requireDatabase().setAuctionBonusLimit(playerUuid, bonus);
    }

    public CompletableFuture<Integer> adjustAuctionBonusLimit(UUID playerUuid, int delta) {
        return requireDatabase().adjustAuctionBonusLimit(playerUuid, delta);
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
