package net.ledok.economy_ld.auction;

import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public record PendingDelivery(
        long id,
        UUID playerUuid,
        ItemStack itemStack,
        int quantity,
        Long lcAmount,
        String reason,
        long expiresAt
) {
}
