package net.ledok.economy_ld.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class AdminShopBlock extends ShopBlock {
    public AdminShopBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.CONSUME;
        }
        if (!serverPlayer.hasPermissions(2)) {
            serverPlayer.sendSystemMessage(Component.literal("Only operators can use admin shops."));
            return InteractionResult.CONSUME;
        }

        if (level.getBlockEntity(pos) instanceof ShopBlockEntity shopBe) {
            initializeShopRecord(level, pos, shopBe, serverPlayer, true);
        }
        serverPlayer.sendSystemMessage(Component.literal("Admin shop opened. GUI is coming in the next step of Phase 3."));
        return InteractionResult.CONSUME;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || !(placer instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (!serverPlayer.hasPermissions(2)) {
            level.destroyBlock(pos, true, serverPlayer);
            serverPlayer.sendSystemMessage(Component.literal("Only operators can place admin shops."));
            return;
        }
        if (level.getBlockEntity(pos) instanceof ShopBlockEntity shopBe) {
            initializeShopRecord(level, pos, shopBe, serverPlayer, true);
        }
    }
}
