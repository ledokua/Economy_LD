package net.ledok.economy_ld.auction;

import net.ledok.economy_ld.manager.EconomyManager;
import net.minecraft.server.MinecraftServer;

public class AuctionExpiryTicker {
    private int auctionTickCounter = 0;
    private int cleanupTickCounter = 0;

    public void onServerTick(MinecraftServer server) {
        if (++auctionTickCounter >= 20) {
            auctionTickCounter = 0;
            EconomyManager.getInstance().processExpiredAuctions(
                    EconomyManager.getInstance().getAuctionConfig_serverTaxPercent(),
                    server.registryAccess()
            ).thenAccept(ignored -> {
            });
        }

        if (++cleanupTickCounter >= 1200) {
            cleanupTickCounter = 0;
            EconomyManager.getInstance().cleanExpiredDeliveries().thenAccept(ignored -> {
            });
        }
    }
}
