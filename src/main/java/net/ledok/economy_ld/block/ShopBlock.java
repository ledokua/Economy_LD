package net.ledok.economy_ld.block;

import com.mojang.serialization.MapCodec;
import net.ledok.economy_ld.manager.EconomyManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

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

        initializeShopRecord(level, pos, shopBe, serverPlayer, false);
        serverPlayer.sendSystemMessage(Component.literal("Shop opened. GUI is coming in the next step of Phase 3."));
        return InteractionResult.CONSUME;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || !(placer instanceof ServerPlayer serverPlayer)) {
            return;
        }

        if (level.getBlockEntity(pos) instanceof ShopBlockEntity shopBe) {
            initializeShopRecord(level, pos, shopBe, serverPlayer, false);
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
                EconomyManager.getInstance().deleteShop(shopBe.getShopId())
                        .whenComplete((ignored, error) -> {});
            }
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    protected void initializeShopRecord(Level level, BlockPos pos, ShopBlockEntity shopBe, ServerPlayer player, boolean admin) {
        if (shopBe.getShopId() == null) {
            shopBe.setShopId(UUID.randomUUID());
        }
        if (admin) {
            shopBe.setOwnerUuid(null);
            shopBe.setAdminShop(true);
        } else {
            shopBe.setOwnerUuid(player.getUUID());
            shopBe.setAdminShop(false);
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
