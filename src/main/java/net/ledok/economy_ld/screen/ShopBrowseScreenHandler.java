package net.ledok.economy_ld.screen;

import net.ledok.economy_ld.shop.ShopListing;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class ShopBrowseScreenHandler extends AbstractContainerMenu {
    private final UUID shopId;
    private final boolean adminShop;
    private final boolean ownerOrOperator;
    private int page;
    private List<ShopListing> listings = new ArrayList<>();

    public ShopBrowseScreenHandler(int syncId, Inventory playerInventory) {
        this(syncId, playerInventory, new UUID(0L, 0L), false, false);
    }

    public ShopBrowseScreenHandler(int syncId, Inventory playerInventory, UUID shopId, boolean adminShop, boolean ownerOrOperator) {
        super(ModMenus.SHOP_BROWSE, syncId);
        this.shopId = shopId;
        this.adminShop = adminShop;
        this.ownerOrOperator = ownerOrOperator;
        this.page = 0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    public UUID getShopId() {
        return shopId;
    }

    public boolean isAdminShop() {
        return adminShop;
    }

    public boolean isOwnerOrOperator() {
        return ownerOrOperator;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = Math.max(0, page);
    }

    public void setListings(List<ShopListing> listings) {
        this.listings = new ArrayList<>(listings);
    }

    public List<ShopListing> getListings() {
        return Collections.unmodifiableList(listings);
    }
}
