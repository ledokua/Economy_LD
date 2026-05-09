package net.ledok.economy_ld.screen;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public class ShopPriceInputScreenHandler extends AbstractContainerMenu {
    private final SimpleContainer inputContainer;
    private final UUID shopId;
    private boolean confirmed;

    public ShopPriceInputScreenHandler(int syncId, Inventory playerInventory) {
        this(syncId, playerInventory, new UUID(0L, 0L));
    }

    public ShopPriceInputScreenHandler(int syncId, Inventory playerInventory, UUID shopId) {
        super(ModMenus.SHOP_PRICE_INPUT, syncId);
        this.shopId = shopId;
        this.inputContainer = new SimpleContainer(1);

        this.addSlot(new Slot(inputContainer, 0, 80, 35));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        moved = stack.copy();

        if (index == 0) {
            if (!this.moveItemStackTo(stack, 1, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, 0, 1, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        return moved;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!confirmed) {
            this.clearContainer(player, inputContainer);
        }
    }

    public UUID getShopId() {
        return shopId;
    }

    public ItemStack getInputItem() {
        return inputContainer.getItem(0);
    }

    public void markConfirmed() {
        this.confirmed = true;
    }
}
