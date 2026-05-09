package net.ledok.economy_ld.shop;

import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public record ShopListing(
        UUID id,
        UUID shopId,
        ItemStack itemStack,
        Long priceBuy,
        Long priceSell,
        int perOp,
        Long buyCap,
        Long stock
) {
}
