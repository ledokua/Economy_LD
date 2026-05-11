package net.ledok.economy_ld.client.screen;

import net.ledok.economy_ld.network.packet.s2c.ShopActionResultS2CPacket;
import net.ledok.economy_ld.shop.ShopListing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class ShopClientState {
    private static final Map<UUID, List<ShopListing>> LISTINGS_BY_SHOP = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> OWNER_BY_SHOP = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> ADMIN_BY_SHOP = new ConcurrentHashMap<>();
    private static final Map<UUID, String> OWNER_LABEL_BY_SHOP = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> OPEN_BALANCE_BY_SHOP = new ConcurrentHashMap<>();
    private static final AtomicReference<UUID> LAST_SHOP_ID = new AtomicReference<>(new UUID(0L, 0L));
    private static final AtomicInteger SYNC_VERSION = new AtomicInteger(0);
    private static final AtomicReference<ShopActionResultS2CPacket> LAST_ACTION_RESULT = new AtomicReference<>(null);
    private static final AtomicInteger ACTION_VERSION = new AtomicInteger(0);

    private ShopClientState() {
    }

    public static void setListings(UUID shopId, boolean adminShop, boolean ownerOrOperator, String ownerLabel, long openerBalance, List<ShopListing> listings) {
        LISTINGS_BY_SHOP.put(shopId, new ArrayList<>(listings));
        OWNER_BY_SHOP.put(shopId, ownerOrOperator);
        ADMIN_BY_SHOP.put(shopId, adminShop);
        OWNER_LABEL_BY_SHOP.put(shopId, ownerLabel);
        OPEN_BALANCE_BY_SHOP.put(shopId, openerBalance);
        LAST_SHOP_ID.set(shopId);
        SYNC_VERSION.incrementAndGet();
    }

    public static int getSyncVersion() {
        return SYNC_VERSION.get();
    }

    public static void setLastActionResult(ShopActionResultS2CPacket result) {
        LAST_ACTION_RESULT.set(result);
        ACTION_VERSION.incrementAndGet();
    }

    public static ShopActionResultS2CPacket getLastActionResult() {
        return LAST_ACTION_RESULT.get();
    }

    public static int getActionVersion() {
        return ACTION_VERSION.get();
    }

    public static List<ShopListing> getListings(UUID shopId) {
        if (shopId.equals(new UUID(0L, 0L))) {
            UUID last = LAST_SHOP_ID.get();
            return LISTINGS_BY_SHOP.getOrDefault(last, Collections.emptyList());
        }
        return LISTINGS_BY_SHOP.getOrDefault(shopId, Collections.emptyList());
    }

    public static UUID getLastShopId() {
        return LAST_SHOP_ID.get();
    }

    public static boolean canManage(UUID shopId) {
        UUID resolved = shopId.equals(new UUID(0L, 0L)) ? LAST_SHOP_ID.get() : shopId;
        return OWNER_BY_SHOP.getOrDefault(resolved, false);
    }

    public static boolean isAdminShop(UUID shopId) {
        UUID resolved = shopId.equals(new UUID(0L, 0L)) ? LAST_SHOP_ID.get() : shopId;
        return ADMIN_BY_SHOP.getOrDefault(resolved, false);
    }

    public static String ownerLabel(UUID shopId) {
        UUID resolved = shopId.equals(new UUID(0L, 0L)) ? LAST_SHOP_ID.get() : shopId;
        return OWNER_LABEL_BY_SHOP.getOrDefault(resolved, "Unknown");
    }

    public static long openerBalance(UUID shopId) {
        UUID resolved = shopId.equals(new UUID(0L, 0L)) ? LAST_SHOP_ID.get() : shopId;
        return OPEN_BALANCE_BY_SHOP.getOrDefault(resolved, 0L);
    }
}
