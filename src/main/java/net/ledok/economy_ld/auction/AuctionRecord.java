package net.ledok.economy_ld.auction;

import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public record AuctionRecord(
        UUID id,
        UUID sellerUuid,
        String sellerName,
        ItemStack itemStack,
        int quantity,
        long startPrice,
        Long buyoutPrice,
        long currentBid,
        UUID bidderUuid,
        String bidderName,
        long expiresAt,
        String status
) {
}
