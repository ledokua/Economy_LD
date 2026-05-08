package net.ledok.economy_ld.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public class ShopBlockEntity extends BlockEntity {
    private UUID shopId;
    private UUID ownerUuid;
    private boolean adminShop;

    public ShopBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.SHOP_BLOCK_ENTITY_TYPE, pos, state);
    }

    public UUID getShopId() {
        return shopId;
    }

    public void setShopId(UUID shopId) {
        this.shopId = shopId;
        setChanged();
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
        setChanged();
    }

    public boolean isAdminShop() {
        return adminShop;
    }

    public void setAdminShop(boolean adminShop) {
        this.adminShop = adminShop;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (shopId != null) {
            tag.putUUID("shop_id", shopId);
        }
        if (ownerUuid != null) {
            tag.putUUID("owner_uuid", ownerUuid);
        }
        tag.putBoolean("admin_shop", adminShop);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.shopId = tag.hasUUID("shop_id") ? tag.getUUID("shop_id") : null;
        this.ownerUuid = tag.hasUUID("owner_uuid") ? tag.getUUID("owner_uuid") : null;
        this.adminShop = tag.getBoolean("admin_shop");
    }
}
