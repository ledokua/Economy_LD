package net.ledok.economy_ld.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.ledok.economy_ld.client.screen.AuctionBrowseScreen;
import net.ledok.economy_ld.client.screen.AuctionClientState;
import net.ledok.economy_ld.client.screen.AuctionInboxScreen;
import net.ledok.economy_ld.client.screen.InboxClientState;
import net.ledok.economy_ld.client.screen.ShopBrowseScreen;
import net.ledok.economy_ld.client.screen.ShopPriceInputScreen;
import net.ledok.economy_ld.network.AuctionNetworking;
import net.ledok.economy_ld.network.ShopNetworking;
import net.ledok.economy_ld.screen.ModMenus;
import net.minecraft.client.gui.screens.MenuScreens;

public class EconomyLdModClient implements ClientModInitializer {

    private int lastOpenVersion = -1;
    private int lastInboxOpenVersion = -1;

    @Override
    public void onInitializeClient() {
        MenuScreens.register(ModMenus.SHOP_BROWSE, ShopBrowseScreen::new);
        MenuScreens.register(ModMenus.SHOP_PRICE_INPUT, ShopPriceInputScreen::new);
        ShopNetworking.registerClientReceivers();
        AuctionNetworking.registerClientReceivers();

        // Open auction screen when server sends OpenAuctionScreenS2CPacket
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            int v = AuctionClientState.getOpenVersion();
            if (lastOpenVersion == -1) {
                lastOpenVersion = v;
                return;
            }
            if (v != lastOpenVersion) {
                lastOpenVersion = v;
                client.setScreen(new AuctionBrowseScreen());
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            int v = InboxClientState.getOpenVersion();
            if (lastInboxOpenVersion == -1) {
                lastInboxOpenVersion = v;
                return;
            }
            if (v != lastInboxOpenVersion) {
                lastInboxOpenVersion = v;
                client.setScreen(new AuctionInboxScreen());
            }
        });
    }
}
