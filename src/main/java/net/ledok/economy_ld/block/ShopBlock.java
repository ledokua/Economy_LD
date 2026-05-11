package net.ledok.economy_ld.block;

import com.mojang.serialization.MapCodec;
import net.ledok.economy_ld.EconomyLdMod;
import net.ledok.economy_ld.manager.EconomyManager;
import net.ledok.economy_ld.network.ShopNetworking;
import net.ledok.economy_ld.shop.ShopListing;
import net.ledok.economy_ld.screen.ShopBrowseScreenHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.UUID;

public class ShopBlock extends BaseEntityBlock {
    public static final MapCodec<ShopBlock> CODEC = simpleCodec(ShopBlock::new);

    public ShopBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShopBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.CONSUME;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ShopBlockEntity shopBe)) {
            return InteractionResult.CONSUME;
        }

        ensureShopRecord(level, pos, shopBe, serverPlayer, false, false);
        UUID shopId = shopBe.getShopId();
        boolean isAdminShop = shopBe.isAdminShop();
        boolean ownerOrOperator = serverPlayer.getUUID().equals(shopBe.getOwnerUuid());

        if (shopId == null) {
            return InteractionResult.CONSUME;
        }
        BlockPos menuPos = pos.immutable();
        serverPlayer.openMenu(new SimpleMenuProvider(
                (syncId, inventory, p) -> new ShopBrowseScreenHandler(syncId, inventory, shopId, isAdminShop, ownerOrOperator, menuPos),
                Component.empty()
        ));
        ShopNetworking.syncShop(serverPlayer, shopId, isAdminShop, ownerOrOperator);
        return InteractionResult.CONSUME;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || !(placer instanceof ServerPlayer serverPlayer)) {
            return;
        }

        if (level.getBlockEntity(pos) instanceof ShopBlockEntity shopBe) {
            ensureShopRecord(level, pos, shopBe, serverPlayer, false, true);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (state.is(newState.getBlock())) {
            super.onRemove(state, level, pos, newState, movedByPiston);
            return;
        }

        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ShopBlockEntity shopBe && shopBe.getShopId() != null) {
                UUID shopId = shopBe.getShopId();
                UUID ownerUuid = shopBe.getOwnerUuid();
                EconomyManager manager = EconomyManager.getInstance();
                manager.getListings(shopId).whenComplete((listings, error) -> {
                    if (level.getServer() == null) {
                        manager.deleteShop(shopId).whenComplete((ignored, deleteError) -> {});
                        return;
                    }
                    level.getServer().execute(() -> {
                        if (error != null) {
                            EconomyLdMod.LOGGER.warn("Failed to load listings while removing shop {}", shopId, error);
                        }
                        returnStockToOwner(level, pos, ownerUuid, error == null && listings != null ? listings : List.of());
                        manager.deleteShop(shopId).whenComplete((ignored, deleteError) -> {
                            if (deleteError != null) {
                                EconomyLdMod.LOGGER.warn("Failed to delete shop {} after block removal", shopId, deleteError);
                            }
                        });
                    });
                });
            }
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private void returnStockToOwner(Level level, BlockPos pos, UUID ownerUuid, List<ShopListing> listings) {
        if (ownerUuid == null || level.getServer() == null) {
            return;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerUuid);
        for (ShopListing listing : listings) {
            if (listing.stock() == null || listing.stock() <= 0) {
                continue;
            }
            String itemName = listing.itemStack().getHoverName().getString();
            long remaining = listing.stock();
            if (owner == null) {
                EconomyLdMod.LOGGER.warn(
                        "Shop removed but owner {} is offline — {} × {} lost",
                        ownerUuid, listing.stock(), itemName
                );
                continue;
            }
            int maxStack = Math.max(1, listing.itemStack().getMaxStackSize());
            while (remaining > 0) {
                int toGiveCount = (int) Math.min(remaining, maxStack);
                ItemStack giveStack = listing.itemStack().copyWithCount(toGiveCount);
                owner.getInventory().add(giveStack);
                if (!giveStack.isEmpty()) {
                    level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, giveStack));
                }
                remaining -= toGiveCount;
            }
        }
    }

    protected void ensureShopRecord(
            Level level,
            BlockPos pos,
            ShopBlockEntity shopBe,
            ServerPlayer player,
            boolean admin,
            boolean allowOwnerUpdate
    ) {
        boolean changed = false;
        if (shopBe.getShopId() == null) {
            shopBe.setShopId(UUID.randomUUID());
            changed = true;
        }
        if (admin) {
            if (shopBe.getOwnerUuid() != null) {
                shopBe.setOwnerUuid(null);
                changed = true;
            }
            if (!shopBe.isAdminShop()) {
                shopBe.setAdminShop(true);
                changed = true;
            }
        } else {
            if (shopBe.isAdminShop()) {
                shopBe.setAdminShop(false);
                changed = true;
            }
            if ((shopBe.getOwnerUuid() == null || allowOwnerUpdate) && !player.getUUID().equals(shopBe.getOwnerUuid())) {
                shopBe.setOwnerUuid(player.getUUID());
                changed = true;
            }
        }

        if (!changed) {
            return;
        }

        EconomyManager.getInstance().upsertShop(
                shopBe.getShopId(),
                shopBe.getOwnerUuid(),
                shopBe.isAdminShop(),
                level.dimension().location(),
                pos
        );
    }
}
