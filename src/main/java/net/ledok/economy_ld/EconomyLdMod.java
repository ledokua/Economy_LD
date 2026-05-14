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
import net.ledok.economy_ld.auction.PendingDelivery;
import net.ledok.economy_ld.manager.EconomyManager;
import net.ledok.economy_ld.network.AuctionNetworking;
import net.ledok.economy_ld.network.ShopNetworking;
import net.ledok.economy_ld.screen.ModMenus;
import net.ledok.economy_ld.util.ServerRegistryAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
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
            EconomyManager.getInstance().claimPendingDeliveries(player.getUUID())
                    .thenAccept(deliveries -> server.execute(() -> {
                        applyPendingDeliveries(player, deliveries);
                        EconomyManager.getInstance().getPendingDeliveries(player.getUUID())
                                .whenComplete((pending, error) -> server.execute(() -> {
                                    if (error == null && pending != null) {
                                        long now = System.currentTimeMillis() / 1000L;
                                        boolean expiringSoon = pending.stream()
                                                .anyMatch(delivery -> delivery.expiresAt() > 0L && delivery.expiresAt() < now + 86400L);
                                        if (expiringSoon) {
                                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                                    "⚠ You have items in your auction inbox expiring within 24 hours! Use /ah inbox to claim them."
                                            ));
                                        }
                                    }
                                    AuctionNetworking.syncInboxToPlayer(player);
                                }));
                    }));
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

    private static void applyPendingDeliveries(ServerPlayer player, java.util.List<PendingDelivery> deliveries) {
        if (deliveries == null || deliveries.isEmpty()) {
            return;
        }

        for (PendingDelivery delivery : deliveries) {
            if (delivery.itemStack() != null) {
                ItemStack give = delivery.itemStack().copyWithCount(Math.max(1, delivery.quantity()));
                if (!player.getInventory().add(give)) {
                    player.drop(give, false);
                }
                continue;
            }

            if (delivery.lcAmount() != null && delivery.lcAmount() > 0L) {
                EconomyManager.getInstance().give(
                        player.getUUID(),
                        player.getName().getString(),
                        delivery.lcAmount()
                );
            }
        }

        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "You received " + deliveries.size() + " pending auction delivery/deliveries."
        ));
    }
}
