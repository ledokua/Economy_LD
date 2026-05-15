package net.ledok.economy_ld;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.ledok.economy_ld.block.ModBlocks;
import net.ledok.economy_ld.command.AuctionCommand;
import net.ledok.economy_ld.command.BalanceCommand;
import net.ledok.economy_ld.command.EcoAdminCommand;
import net.ledok.economy_ld.command.PayCommand;
import net.ledok.economy_ld.auction.AuctionExpiryTicker;
import net.ledok.economy_ld.manager.EconomyManager;
import net.ledok.economy_ld.network.AuctionNetworking;
import net.ledok.economy_ld.network.ShopNetworking;
import net.ledok.economy_ld.screen.ModMenus;
import net.ledok.economy_ld.util.ServerRegistryAccess;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EconomyLdMod implements ModInitializer {
    public static final String MOD_ID = "economy_ld";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        EconomyManager.initialize(LOGGER);
        ModBlocks.register();
        ModMenus.register();
        ShopNetworking.registerPayloadTypes();
        AuctionNetworking.registerPayloadTypes();
        ShopNetworking.registerServerReceivers();
        AuctionNetworking.registerServerReceivers();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            BalanceCommand.register(dispatcher);
            PayCommand.register(dispatcher);
            EcoAdminCommand.register(dispatcher);
            AuctionCommand.register(dispatcher);
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> ServerRegistryAccess.set(server.registryAccess()));

        AuctionExpiryTicker auctionTicker = new AuctionExpiryTicker();
        ServerTickEvents.END_SERVER_TICK.register(auctionTicker::onServerTick);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            EconomyManager.getInstance().getPendingDeliveries(player.getUUID())
                    .thenAccept(deliveries -> server.execute(() -> {
                        long now = System.currentTimeMillis() / 1000L;
                        boolean expiringSoon = deliveries.stream()
                                .anyMatch(d -> d.expiresAt() > 0L && d.expiresAt() < now + 86400L);
                        if (expiringSoon) {
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                    "⚠ You have items in your auction inbox expiring within 24 hours! Use /ah inbox to claim them."
                            ));
                        }
                    }));
            AuctionNetworking.syncInboxToPlayer(player);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                EconomyManager.getInstance().clearAdminMode(handler.player.getUUID()));

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ServerRegistryAccess.clear();
            try {
                EconomyManager.getInstance().shutdown();
            } catch (Exception e) {
                LOGGER.error("Error while shutting down economy manager", e);
            }
        });
    }

}
