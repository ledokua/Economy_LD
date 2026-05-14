package net.ledok.economy_ld.client.screen;

import net.ledok.economy_ld.auction.PendingDelivery;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public final class InboxClientState {
    private static final List<PendingDelivery> DELIVERIES = new CopyOnWriteArrayList<>();
    private static final AtomicInteger SYNC_VERSION = new AtomicInteger(0);
    private static final AtomicInteger OPEN_VERSION = new AtomicInteger(0);

    private InboxClientState() {
    }

    public static void setDeliveries(List<PendingDelivery> deliveries) {
        DELIVERIES.clear();
        DELIVERIES.addAll(deliveries);
        SYNC_VERSION.incrementAndGet();
    }

    public static List<PendingDelivery> getDeliveries() {
        return Collections.unmodifiableList(DELIVERIES);
    }

    public static int getSyncVersion() {
        return SYNC_VERSION.get();
    }

    public static void markOpenRequested() {
        OPEN_VERSION.incrementAndGet();
    }

    public static int getOpenVersion() {
        return OPEN_VERSION.get();
    }
}
