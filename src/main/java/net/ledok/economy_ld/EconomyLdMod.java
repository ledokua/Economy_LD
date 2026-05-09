package net.ledok.economy_ld;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.ledok.economy_ld.block.ModBlocks;
import net.ledok.economy_ld.command.BalanceCommand;
import net.ledok.economy_ld.command.EcoAdminCommand;
import net.ledok.economy_ld.command.PayCommand;
import net.ledok.economy_ld.manager.EconomyManager;
import net.ledok.economy_ld.network.ShopNetworking;
import net.ledok.economy_ld.screen.ModMenus;
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
        ShopNetworking.registerServerReceivers();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            BalanceCommand.register(dispatcher);
            PayCommand.register(dispatcher);
            EcoAdminCommand.register(dispatcher);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            try {
                EconomyManager.getInstance().shutdown();
            } catch (Exception e) {
                LOGGER.error("Error while shutting down economy manager", e);
            }
        });
    }
}
