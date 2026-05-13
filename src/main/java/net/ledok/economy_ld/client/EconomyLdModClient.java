package net.ledok.economy_ld.client;

import net.fabricmc.api.ClientModInitializer;
import net.ledok.economy_ld.client.screen.ShopBrowseScreen;
import net.ledok.economy_ld.client.screen.ShopPriceInputScreen;
import net.ledok.economy_ld.network.AuctionNetworking;
import net.ledok.economy_ld.network.ShopNetworking;
import net.ledok.economy_ld.screen.ModMenus;
import net.minecraft.client.gui.screens.MenuScreens;

public class EconomyLdModClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        MenuScreens.register(ModMenus.SHOP_BROWSE, ShopBrowseScreen::new);
        MenuScreens.register(ModMenus.SHOP_PRICE_INPUT, ShopPriceInputScreen::new);
        ShopNetworking.registerClientReceivers();
        AuctionNetworking.registerClientReceivers();
    }
}
