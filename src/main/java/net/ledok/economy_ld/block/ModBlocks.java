package net.ledok.economy_ld.block;

import net.ledok.economy_ld.EconomyLdMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlocks {
    public static final ShopBlock SHOP_BLOCK = new ShopBlock(Block.Properties.of().strength(2.0F));
    public static final AdminShopBlock ADMIN_SHOP_BLOCK = new AdminShopBlock(Block.Properties.of().strength(2.0F));

    public static BlockEntityType<ShopBlockEntity> SHOP_BLOCK_ENTITY_TYPE;

    private ModBlocks() {
    }

    public static void register() {
        Registry.register(BuiltInRegistries.BLOCK, id("shop_block"), SHOP_BLOCK);
        Registry.register(BuiltInRegistries.BLOCK, id("admin_shop_block"), ADMIN_SHOP_BLOCK);

        Registry.register(BuiltInRegistries.ITEM, id("shop_block"), new BlockItem(SHOP_BLOCK, new Item.Properties()));
        Registry.register(BuiltInRegistries.ITEM, id("admin_shop_block"), new BlockItem(ADMIN_SHOP_BLOCK, new Item.Properties()));

        SHOP_BLOCK_ENTITY_TYPE = Registry.register(
                BuiltInRegistries.BLOCK_ENTITY_TYPE,
                id("shop_block_entity"),
                BlockEntityType.Builder.of(ShopBlockEntity::new, SHOP_BLOCK, ADMIN_SHOP_BLOCK).build(null)
        );
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(EconomyLdMod.MOD_ID, path);
    }
}
