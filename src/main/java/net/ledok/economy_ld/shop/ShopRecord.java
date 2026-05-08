package net.ledok.economy_ld.shop;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record ShopRecord(
        UUID id,
        UUID ownerUuid,
        boolean admin,
        ResourceLocation dimension,
        BlockPos pos,
        long createdAt
) {
}
