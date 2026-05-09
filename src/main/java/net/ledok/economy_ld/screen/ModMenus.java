package net.ledok.economy_ld.screen;

import net.ledok.economy_ld.EconomyLdMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

public final class ModMenus {
    public static MenuType<ShopBrowseScreenHandler> SHOP_BROWSE;
    public static MenuType<ShopPriceInputScreenHandler> SHOP_PRICE_INPUT;

    private ModMenus() {
    }

    public static void register() {
        SHOP_BROWSE = Registry.register(
                BuiltInRegistries.MENU,
                id("shop_browse"),
                new MenuType<>(ShopBrowseScreenHandler::new, FeatureFlags.VANILLA_SET)
        );
        SHOP_PRICE_INPUT = Registry.register(
                BuiltInRegistries.MENU,
                id("shop_price_input"),
                new MenuType<>(ShopPriceInputScreenHandler::new, FeatureFlags.VANILLA_SET)
        );
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(EconomyLdMod.MOD_ID, path);
    }
}
