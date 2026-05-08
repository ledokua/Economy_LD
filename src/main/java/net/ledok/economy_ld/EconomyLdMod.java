package net.ledok.economy_ld;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.ledok.economy_ld.config.ConfigLoader;
import net.ledok.economy_ld.config.EconomyConfig;
import net.ledok.economy_ld.manager.EconomyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EconomyLdMod implements ModInitializer {
    public static final String MOD_ID = "economy_ld";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        EconomyConfig config = ConfigLoader.load(LOGGER);
        EconomyManager.getInstance().start(config, LOGGER);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> EconomyManager.getInstance().stop());
    }
}
