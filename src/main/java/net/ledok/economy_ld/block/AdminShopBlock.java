package net.ledok.economy_ld.block;

import com.mojang.serialization.MapCodec;
import net.ledok.economy_ld.manager.EconomyManager;
import net.ledok.economy_ld.util.PermissionHelper;
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
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.phys.BlockHitResult;
import net.ledok.economy_ld.network.ShopNetworking;
import net.ledok.economy_ld.screen.ShopBrowseScreenHandler;

import java.util.UUID;

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

        if (level.getBlockEntity(pos) instanceof ShopBlockEntity shopBe) {
            ensureShopRecord(level, pos, shopBe, serverPlayer, true, false);
            UUID shopId = shopBe.getShopId();
            boolean isAdminShop = shopBe.isAdminShop();
            boolean ownerOrOperator = PermissionHelper.check(serverPlayer, "economy_ld.admin.shop.manage", 2)
                    && EconomyManager.getInstance().isAdminModeActive(serverPlayer.getUUID());
            if (shopId == null) {
                return InteractionResult.CONSUME;
            }
            BlockPos menuPos = pos.immutable();
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (syncId, inventory, p) -> new ShopBrowseScreenHandler(syncId, inventory, shopId, isAdminShop, ownerOrOperator, menuPos),
                    Component.empty()
            ));
            ShopNetworking.syncShop(serverPlayer, shopId, isAdminShop, ownerOrOperator);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (!(placer instanceof ServerPlayer serverPlayer)
                || !PermissionHelper.check(serverPlayer, "economy_ld.admin.shop.place", 2)) {
            if (!level.isClientSide() && placer instanceof ServerPlayer sp) {
                sp.sendSystemMessage(Component.translatable("economy_ld.block.admin_shop.no_permission"));
                level.destroyBlock(pos, true, placer);
            }
            return;
        }
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof ShopBlockEntity shopBe) {
            ensureShopRecord(level, pos, shopBe, serverPlayer, true, true);
        }
    }
}
