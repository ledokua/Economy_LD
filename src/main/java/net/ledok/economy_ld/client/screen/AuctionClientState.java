package net.ledok.economy_ld.client.screen;

import net.ledok.economy_ld.auction.AuctionRecord;
import net.ledok.economy_ld.network.packet.s2c.AuctionActionResultS2CPacket;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class AuctionClientState {
    private static final List<AuctionRecord> AUCTIONS = new CopyOnWriteArrayList<>();
    private static final AtomicInteger SYNC_VERSION = new AtomicInteger(0);
    private static final AtomicInteger ACTION_VERSION = new AtomicInteger(0);
    private static final AtomicReference<AuctionActionResultS2CPacket> LAST_RESULT = new AtomicReference<>(null);
    private static final AtomicInteger OPEN_VERSION = new AtomicInteger(0);
    private static final java.util.concurrent.atomic.AtomicLong PLAYER_BALANCE = new java.util.concurrent.atomic.AtomicLong(0L);
    private static final AtomicInteger LISTING_FEE_PERCENT = new AtomicInteger(5);

    private AuctionClientState() {
    }

    public static void setAuctions(List<AuctionRecord> auctions) {
        AUCTIONS.clear();
        AUCTIONS.addAll(auctions);
        SYNC_VERSION.incrementAndGet();
    }

    public static void setPlayerBalance(long balance) {
        PLAYER_BALANCE.set(balance);
    }

    public static long getPlayerBalance() {
        return PLAYER_BALANCE.get();
    }

    public static void setListingFeePercent(int percent) {
        LISTING_FEE_PERCENT.set(percent);
    }

    public static int getListingFeePercent() {
        return LISTING_FEE_PERCENT.get();
    }

    public static List<AuctionRecord> getAuctions() {
        return Collections.unmodifiableList(AUCTIONS);
    }

    public static int getSyncVersion() {
        return SYNC_VERSION.get();
    }

    public static void setLastResult(AuctionActionResultS2CPacket result) {
        LAST_RESULT.set(result);
        ACTION_VERSION.incrementAndGet();
    }

    public static AuctionActionResultS2CPacket getLastResult() {
        return LAST_RESULT.get();
    }

    public static int getActionVersion() {
        return ACTION_VERSION.get();
    }

    public static void markOpenRequested() {
        OPEN_VERSION.incrementAndGet();
    }

    public static int getOpenVersion() {
        return OPEN_VERSION.get();
    }
}
