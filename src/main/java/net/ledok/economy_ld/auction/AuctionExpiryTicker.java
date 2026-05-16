package net.ledok.economy_ld.auction;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.ledok.economy_ld.manager.EconomyManager;
import net.ledok.economy_ld.network.AuctionNetworking;
import net.ledok.economy_ld.network.packet.s2c.AuctionActionResultS2CPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class AuctionExpiryTicker {
    private int auctionTickCounter = 0;
    private int cleanupTickCounter = 0;

    public void onServerTick(MinecraftServer server) {
        if (++auctionTickCounter >= 20) {
            auctionTickCounter = 0;
            EconomyManager.getInstance().processExpiredAuctions(
                    EconomyManager.getInstance().getAuctionConfig_serverTaxPercent(),
                    server.registryAccess()
            ).thenAccept(affectedUuids -> server.execute(() -> {
                for (UUID uuid : affectedUuids) {
                    ServerPlayer online = server.getPlayerList().getPlayer(uuid);
                    if (online == null) {
                        continue;
                    }
                    AuctionNetworking.syncInboxToPlayer(online);
                    online.sendSystemMessage(Component.literal(
                            "📦 You have new items in your auction inbox! Use /ah inbox to claim them."
                    ));
                    ServerPlayNetworking.send(online, new AuctionActionResultS2CPacket(
                            AuctionActionResultS2CPacket.ActionType.ITEM_SENT_TO_INBOX,
                            "",
                            0L
                    ));
                }
            }));
        }

        if (++cleanupTickCounter >= 1200) {
            cleanupTickCounter = 0;
            EconomyManager.getInstance().cleanExpiredDeliveries().thenAccept(ignored -> {
            });
        }
    }
}
