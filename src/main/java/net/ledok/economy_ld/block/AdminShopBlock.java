package net.ledok.economy_ld.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class AdminShopBlock extends ShopBlock {
    public static final MapCodec<AdminShopBlock> CODEC = simpleCodec(AdminShopBlock::new);

    public AdminShopBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
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
        if (!(placer instanceof ServerPlayer serverPlayer) || !serverPlayer.hasPermissions(2)) {
            if (!level.isClientSide() && placer instanceof ServerPlayer sp) {
                sp.sendSystemMessage(Component.literal("Only operators can place admin shops."));
                level.destroyBlock(pos, true, placer);
            }
            return;
        }
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof ShopBlockEntity shopBe) {
            initializeShopRecord(level, pos, shopBe, serverPlayer, true);
        }
    }
}
