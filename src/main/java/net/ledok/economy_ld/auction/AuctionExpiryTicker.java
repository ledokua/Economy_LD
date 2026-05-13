package net.ledok.economy_ld.auction;

import net.ledok.economy_ld.manager.EconomyManager;
import net.minecraft.server.MinecraftServer;

public class AuctionExpiryTicker {
    private int tickCounter = 0;

    public void onServerTick(MinecraftServer server) {
        if (++tickCounter < 20) {
            return;
        }
        tickCounter = 0;

        EconomyManager.getInstance().processExpiredAuctions(
                EconomyManager.getInstance().getAuctionConfig_serverTaxPercent(),
                server.registryAccess()
        ).thenAccept(ignored -> {
        });
    }
}
