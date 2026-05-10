package net.ledok.economy_ld.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import io.wispforest.owo.ui.base.BaseOwoHandledScreen;
import io.wispforest.owo.ui.component.BoxComponent;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.OverlayContainer;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.ledok.economy_ld.network.packet.c2s.AddListingC2SPacket;
import net.ledok.economy_ld.screen.ShopBrowseScreenHandler;
import net.ledok.economy_ld.shop.ShopListing;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.UUID;

public class ShopBrowseScreen extends BaseOwoHandledScreen<StackLayout, ShopBrowseScreenHandler> {
    private static final UUID ZERO_UUID = new UUID(0L, 0L);
    private static final int LISTINGS_PER_PAGE = 5;

    private LabelComponent ownerLabel;
    private LabelComponent listingCountLabel;
    private LabelComponent balanceLabel;
    private LabelComponent pageLabel;
    private ButtonComponent prevButton;
    private ButtonComponent nextButton;
    private ButtonComponent newListingButton;
    private ButtonComponent firstListingButton;
    private FlowLayout pageBars;
    private StackLayout rootLayout;
    private OverlayContainer<FlowLayout> listingDialog;

    private int lastTotalPages = -1;
    private int lastCurrentPage = -1;

    public ShopBrowseScreen(ShopBrowseScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.inventoryLabelY = 9999;
        this.titleLabelY = 9999;
    }

    @Override
    protected @NotNull OwoUIAdapter<StackLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::stack);
    }

    @Override
    protected void build(StackLayout rootComponent) {
        this.rootLayout = rootComponent;
        rootComponent.surface(Surface.flat(0xFF070E14));

        FlowLayout mainLayer = Containers.verticalFlow(Sizing.fill(100), Sizing.fill(100));
        mainLayer.padding(Insets.of(16));
        mainLayer.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        FlowLayout shell = Containers.verticalFlow(Sizing.fill(98), Sizing.fixed(446));
        shell.surface(Surface.flat(0xFF0D151F).and(Surface.outline(0xFF2F4155)));
        shell.padding(Insets.of(1));

        FlowLayout panel = Containers.verticalFlow(Sizing.fill(100), Sizing.fill(100));
        panel.surface(Surface.flat(0xFF0B141E));
        panel.child(buildHeader());
        panel.child(buildEmptyBody());
        panel.child(buildFooter());

        shell.child(panel);
        mainLayer.child(shell);
        rootComponent.child(mainLayer);

        refreshUi(true);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refreshUi(false);
    }

    private void refreshUi(boolean force) {
        if (this.listingDialog != null && !this.listingDialog.hasParent()) {
            this.listingDialog = null;
        }

        UUID shopId = activeShopId();
        List<ShopListing> listings = ShopClientState.getListings(shopId);
        boolean adminShop = ShopClientState.isAdminShop(shopId);
        boolean canManage = ShopClientState.canManage(shopId);
        String owner = adminShop ? "Server" : ShopClientState.ownerLabel(shopId);
        long balance = ShopClientState.openerBalance(shopId);

        int totalPages = Math.max(1, (listings.size() + LISTINGS_PER_PAGE - 1) / LISTINGS_PER_PAGE);
        if (this.menu.getPage() >= totalPages) {
            this.menu.setPage(totalPages - 1);
        }
        int currentPage = this.menu.getPage() + 1;

        this.ownerLabel.text(Component.literal("OWNER  ·  " + owner));
        this.listingCountLabel.text(Component.literal("LISTINGS  ·  " + listings.size()));
        this.balanceLabel.text(Component.literal("BAL  ·  " + String.format("%,d", balance) + " LC"));

        this.pageLabel.text(Component.literal("PAGE  " + currentPage + " / " + totalPages));
        this.prevButton.active(currentPage > 1);
        this.nextButton.active(currentPage < totalPages);
        this.newListingButton.active(canManage);
        this.firstListingButton.active(canManage);

        if (force || this.lastTotalPages != totalPages || this.lastCurrentPage != currentPage) {
            rebuildPageBars(totalPages, currentPage);
            this.lastTotalPages = totalPages;
            this.lastCurrentPage = currentPage;
        }
    }

    private FlowLayout buildHeader() {
        FlowLayout header = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(64));
        header.surface(Surface.flat(0xFF0D1722).and(Surface.outline(0xFF1F3042)));
        header.padding(Insets.of(8));
        header.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        header.gap(10);

        FlowLayout icon = Containers.verticalFlow(Sizing.fixed(34), Sizing.fixed(34));
        icon.surface(Surface.flat(0xFF9C7AE8));
        icon.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        icon.child(tint("₴", 0xFF201333));

        FlowLayout title = Containers.verticalFlow(Sizing.expand(), Sizing.content());
        title.gap(1);
        title.child(tint("LeDok's Wares", 0xFFE7EDF5));

        FlowLayout subtitle = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        subtitle.gap(6);
        subtitle.child(tag("PLAYER SHOP"));
        this.ownerLabel = tint("OWNER  ·  Unknown", 0xFF8FA1B5);
        this.listingCountLabel = tint("LISTINGS  ·  0", 0xFF8FA1B5);
        this.balanceLabel = tint("BAL  ·  0 LC", 0xFF9D81EA);
        subtitle.child(this.ownerLabel);
        subtitle.child(this.listingCountLabel);
        subtitle.child(this.balanceLabel);
        title.child(subtitle);

        FlowLayout controls = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        controls.gap(6);
        controls.child(smallButton("⌕", 38, 32, 0xFF132131, 0xFF193047, 0xFF334A60));
        controls.child(smallButton("SORT · NAME", 110, 32, 0xFF132131, 0xFF193047, 0xFF334A60));
        this.newListingButton = smallButton("+ NEW LISTING", 132, 32, 0xFF9A77E8, 0xFFAA8AF0, 0xFFC4AFFF, button -> openListingDialog());
        controls.child(this.newListingButton);
        controls.child(smallButton("⋯", 38, 32, 0xFF132131, 0xFF193047, 0xFF334A60));
        controls.child(smallButton("✕", 32, 32, 0xFF132131, 0xFF193047, 0xFF334A60, button -> this.onClose()));

        header.child(icon);
        header.child(title);
        header.child(controls);
        return header;
    }

    private FlowLayout buildEmptyBody() {
        Surface striped = Surface.flat(0xFF111C29).and((context, component) -> {
            int x = component.x();
            int y = component.y();
            int w = component.width();
            int h = component.height();
            for (int row = 0; row < h; row += 18) {
                context.fill(x, y + row, x + w, y + row + 9, 0xFF0D1722);
            }
        }).and(Surface.outline(0xFF1C2D3F));

        FlowLayout body = Containers.verticalFlow(Sizing.fill(100), Sizing.expand());
        body.surface(striped);
        body.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        body.gap(8);

        FlowLayout plusBox = Containers.verticalFlow(Sizing.fixed(78), Sizing.fixed(78));
        plusBox.surface(Surface.flat(0x0).and(Surface.outline(0xFF40607D)));
        plusBox.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        plusBox.child(tint("+", 0xFF7A95AF));

        body.child(plusBox);
        body.child(tint("No listings yet", 0xFFE4EBF4));
        body.child(tint("Drop an item from your inventory or click + New", 0xFF93A3B6));
        body.child(tint("Listing to begin trading.", 0xFF93A3B6));
        this.firstListingButton = largeCta("+ ADD YOUR FIRST LISTING", button -> openListingDialog());
        body.child(this.firstListingButton);

        return body;
    }

    private FlowLayout buildFooter() {
        FlowLayout footer = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(58));
        footer.surface(Surface.flat(0xFF0B141E).and(Surface.outline(0xFF1F3042)));
        footer.padding(Insets.of(12));
        footer.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        this.prevButton = smallButton("◀ PREV", 78, 30, 0xFF1A2736, 0xFF223347, 0xFF3A4F67);
        this.prevButton.onPress(button -> {
            this.menu.setPage(Math.max(0, this.menu.getPage() - 1));
            refreshUi(true);
        });

        this.nextButton = smallButton("NEXT ▶", 78, 30, 0xFF1A2736, 0xFF223347, 0xFF3A4F67);
        this.nextButton.onPress(button -> {
            this.menu.setPage(this.menu.getPage() + 1);
            refreshUi(true);
        });

        FlowLayout center = Containers.horizontalFlow(Sizing.expand(), Sizing.content());
        center.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        center.gap(10);

        this.pageBars = Containers.horizontalFlow(Sizing.fixed(140), Sizing.fixed(4));
        this.pageBars.gap(2);

        this.pageLabel = tint("PAGE  1 / 1", 0xFFD0DBE7);
        center.child(this.pageBars);
        center.child(this.pageLabel);

        footer.child(this.prevButton);
        footer.child(center);
        footer.child(this.nextButton);
        return footer;
    }

    private void rebuildPageBars(int totalPages, int currentPage) {
        this.pageBars.clearChildren();
        for (int i = 1; i <= totalPages; i++) {
            BoxComponent bar = Components.box(Sizing.fill(100), Sizing.fill(100));
            bar.fill(true);
            if (i == currentPage) {
                bar.color(Color.ofRgb(0xA875FF));
            } else {
                bar.color(Color.ofRgb(0x3B4D61));
            }
            this.pageBars.child(bar);
        }
    }

    private ButtonComponent largeCta(String text, java.util.function.Consumer<ButtonComponent> onPress) {
        ButtonComponent button = Components.button(Component.literal(text), onPress);
        button.sizing(Sizing.fixed(254), Sizing.fixed(44));
        button.renderer((context, rendered, delta) -> {
            int color = rendered.active() ? (rendered.isHoveredOrFocused() ? 0xFFAA8AF0 : 0xFF9A77E8) : 0xFF3C4A5D;
            int border = rendered.active() ? 0xFFBC9CFF : 0xFF5A6678;
            context.fill(rendered.getX(), rendered.getY(), rendered.getX() + rendered.getWidth(), rendered.getY() + rendered.getHeight(), color);
            context.drawRectOutline(rendered.getX(), rendered.getY(), rendered.getWidth(), rendered.getHeight(), border);
        });
        return button;
    }

    private FlowLayout tag(String text) {
        FlowLayout tag = Containers.verticalFlow(Sizing.content(), Sizing.content());
        tag.surface(Surface.flat(0xFF132A44).and(Surface.outline(0xFF2C4D72)));
        tag.padding(Insets.both(6, 1));
        tag.child(tint(text, 0xFF84A6CF));
        return tag;
    }

    private ButtonComponent smallButton(String text, int width, int height, int fill, int hover, int border) {
        return smallButton(text, width, height, fill, hover, border, b -> {});
    }

    private ButtonComponent smallButton(String text, int width, int height, int fill, int hover, int border, java.util.function.Consumer<ButtonComponent> onPress) {
        ButtonComponent button = Components.button(Component.literal(text), onPress);
        button.sizing(Sizing.fixed(width), Sizing.fixed(height));
        button.renderer((context, rendered, delta) -> {
            int color = rendered.isHoveredOrFocused() ? hover : fill;
            context.fill(rendered.getX(), rendered.getY(), rendered.getX() + rendered.getWidth(), rendered.getY() + rendered.getHeight(), color);
            context.drawRectOutline(rendered.getX(), rendered.getY(), rendered.getWidth(), rendered.getHeight(), border);
        });
        return button;
    }

    private LabelComponent tint(String text, int color) {
        LabelComponent label = Components.label(Component.literal(text));
        label.color(Color.ofArgb(color));
        return label;
    }

    private void openListingDialog() {
        if (this.listingDialog != null) {
            return;
        }

        final ItemStack previewStack = (this.minecraft != null && this.minecraft.player != null)
                ? this.minecraft.player.getMainHandItem().copyWithCount(1)
                : ItemStack.EMPTY;

        final String[] buyValue = {"0"};
        final String[] sellValue = {"0"};
        final String[] perOpValue = {"1"};
        final String[] buyCapValue = {"256"};

        FlowLayout panel = Containers.verticalFlow(Sizing.fixed(500), Sizing.content());
        panel.surface(Surface.flat(0xFF111C28).and(Surface.outline(0xFF4E6177)));
        panel.padding(Insets.of(10));
        panel.gap(8);

        FlowLayout head = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        head.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        FlowLayout titlePack = Containers.verticalFlow(Sizing.expand(), Sizing.content());
        titlePack.gap(1);
        titlePack.child(tint("Edit Listing", 0xFFE6EDF7));
        titlePack.child(tint((previewStack.isEmpty() ? "EMPTY" : previewStack.getItem().toString().toUpperCase()), 0xFF7E93AA));
        head.child(titlePack);
        head.child(Containers.horizontalFlow(Sizing.expand(), Sizing.content()));
        head.child(smallButton("✕", 28, 28, 0xFF132131, 0xFF193047, 0xFF334A60, button -> closeListingDialog()));

        FlowLayout body = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        body.surface(Surface.flat(0xFF1A2A3A).and(Surface.outline(0xFF334A60)));
        body.padding(Insets.of(12));
        body.gap(10);

        FlowLayout itemCard = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        itemCard.surface(Surface.flat(0xFF202E3F).and(Surface.outline(0xFF3B526B)));
        itemCard.padding(Insets.of(12));
        itemCard.gap(10);
        itemCard.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        itemCard.child(Components.item(previewStack).showOverlay(true).setTooltipFromStack(true));
        FlowLayout itemCardText = Containers.verticalFlow(Sizing.expand(), Sizing.content());
        itemCardText.gap(2);
        itemCardText.child(tint(previewStack.isEmpty() ? "No Item In Main Hand" : previewStack.getHoverName().getString(), 0xFFE6EDF7));
        itemCardText.child(tint("x1  IN STOCK", 0xFF8DA2B8));
        itemCard.child(itemCardText);
        body.child(itemCard);

        LabelComponent previewLine1 = tint("", 0xFF91C982);
        LabelComponent previewLine2 = tint("", 0xFFA88FE7);

        FlowLayout row1 = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row1.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        row1.gap(12);
        FieldUi buyField = createDialogField(row1, "BUY PRICE", "player buys from shop", "0", "LC", buyValue);
        FieldUi sellField = createDialogField(row1, "SELL PRICE", "shop buys from player", "0", "LC", sellValue);
        body.child(row1);

        FlowLayout row2 = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row2.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        row2.gap(12);
        FieldUi perOpField = createDialogField(row2, "ITEMS PER OP", "per buy / sell click", "1", "x", perOpValue);
        FieldUi buyCapField = createDialogField(row2, "BUY CAP", "max stock from players", "256", "max", buyCapValue);
        body.child(row2);

        FlowLayout summary = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        summary.surface(Surface.flat(0xFF202E3F).and(Surface.outline(0xFF3B526B)));
        summary.padding(Insets.of(8));
        summary.gap(4);
        summary.child(previewLine1);
        summary.child(previewLine2);
        body.child(summary);

        FlowLayout errorBanner = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(38));
        errorBanner.padding(Insets.of(8));
        errorBanner.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        LabelComponent errorText = tint("⚠ SET AT LEAST ONE PRICE.", 0xFFE7B69F);
        errorBanner.child(errorText);
        body.child(errorBanner);

        Runnable refreshPreview = () -> {
            long buy = parseLongField(buyValue[0], 0);
            long sell = parseLongField(sellValue[0], 0);
            long perOp = Math.max(1, parseLongField(perOpValue[0], 1));
            long buyCap = Math.max(0, parseLongField(buyCapValue[0], 0));

            boolean valid = buy > 0 || sell > 0;
            applyFieldStyle(buyField, buy > 0 ? FieldStyle.ACTIVE : FieldStyle.INVALID);
            applyFieldStyle(sellField, sell > 0 ? FieldStyle.ACTIVE : FieldStyle.INVALID);
            applyFieldStyle(perOpField, FieldStyle.ACTIVE);
            applyFieldStyle(buyCapField, sell > 0 ? FieldStyle.ACTIVE : FieldStyle.DIMMED);

            if (buy > 0) {
                previewLine1.text(Component.literal("→ PLAYER BUYS ×" + perOp + " FOR " + String.format("%,d", buy) + " LC"));
            } else {
                previewLine1.text(Component.literal(""));
            }
            if (sell > 0) {
                if (buyCap > 0) {
                    previewLine2.text(Component.literal("← SHOP BUYS ×" + perOp + " FOR " + String.format("%,d", sell) + " LC UP TO " + buyCap));
                } else {
                    previewLine2.text(Component.literal("← SHOP BUYS ×" + perOp + " FOR " + String.format("%,d", sell) + " LC"));
                }
            } else {
                previewLine2.text(Component.literal(""));
            }

            if (valid) {
                errorBanner.surface(Surface.flat(0x00000000));
                errorText.text(Component.literal(""));
            } else {
                errorBanner.surface(Surface.flat(0xFF5A231D).and(Surface.outline(0xFF9B3F33)));
                errorText.text(Component.literal("⚠ SET AT LEAST ONE PRICE."));
            }
        };
        buyField.input().onChanged().subscribe(value -> refreshPreview.run());
        sellField.input().onChanged().subscribe(value -> refreshPreview.run());
        perOpField.input().onChanged().subscribe(value -> refreshPreview.run());
        buyCapField.input().onChanged().subscribe(value -> refreshPreview.run());
        refreshPreview.run();

        FlowLayout actions = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        actions.alignment(HorizontalAlignment.RIGHT, VerticalAlignment.CENTER);
        actions.gap(6);
        actions.child(smallButton("CANCEL", 98, 32, 0xFF1B2A3B, 0xFF22354A, 0xFF3B526A, b -> closeListingDialog()));
        actions.child(smallButton("CONFIRM", 108, 32, 0xFF9A77E8, 0xFFAA8AF0, 0xFFC4AFFF, b -> {
            long buy = parseLongField(buyValue[0], 0);
            long sell = parseLongField(sellValue[0], 0);
            if (buy <= 0 && sell <= 0) {
                errorBanner.surface(Surface.flat(0xFF5A231D).and(Surface.outline(0xFF9B3F33)));
                errorText.text(Component.literal("⚠ SET AT LEAST ONE PRICE."));
                return;
            }
            if (previewStack.isEmpty()) {
                errorBanner.surface(Surface.flat(0xFF5A231D).and(Surface.outline(0xFF9B3F33)));
                errorText.text(Component.literal("⚠ HOLD AN ITEM IN MAIN HAND."));
                return;
            }
            if (this.minecraft == null || this.minecraft.level == null) {
                return;
            }

            long perOp = Math.max(1, parseLongField(perOpValue[0], 1));
            long buyCap = Math.max(0, parseLongField(buyCapValue[0], 0));
            if (!(previewStack.saveOptional(this.minecraft.level.registryAccess()) instanceof CompoundTag itemNbt)) {
                errorBanner.surface(Surface.flat(0xFF5A231D).and(Surface.outline(0xFF9B3F33)));
                errorText.text(Component.literal("⚠ FAILED TO SERIALIZE ITEM."));
                return;
            }

            Long priceBuy = buy > 0 ? buy : null;
            Long priceSell = sell > 0 ? sell : null;
            Long packetBuyCap = buyCap > 0 ? buyCap : null;

            ClientPlayNetworking.send(new AddListingC2SPacket(
                    activeShopId(),
                    itemNbt,
                    priceBuy,
                    priceSell,
                    (int) Math.min(Integer.MAX_VALUE, perOp),
                    packetBuyCap
            ));
            closeListingDialog();
        }));

        panel.child(head);
        panel.child(body);
        panel.child(actions);

        this.listingDialog = Containers.overlay(panel);
        this.listingDialog.closeOnClick(false);
        this.listingDialog.surface(Surface.flat(0x88000000));
        this.listingDialog.zIndex(300);
        this.rootLayout.child(this.listingDialog);
    }

    private FieldUi createDialogField(
            FlowLayout row,
            String title,
            String hint,
            String initial,
            String suffix,
            String[] stateRef
    ) {
        FlowLayout field = Containers.verticalFlow(Sizing.fixed(220), Sizing.content());
        field.gap(4);

        FlowLayout head = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        head.child(tint(title, 0xFF9CB2C8));
        head.child(Containers.horizontalFlow(Sizing.expand(), Sizing.content()));
        head.child(tint(hint, 0xFF6F849A));
        field.child(head);

        FlowLayout inputWrap = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(40));
        inputWrap.surface(Surface.flat(0xFF111C28).and(Surface.outline(0xFF48627D)));
        inputWrap.padding(Insets.of(6));
        inputWrap.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        inputWrap.gap(6);

        TextBoxComponent input = Components.textBox(Sizing.expand(), initial);
        input.setMaxLength(12);
        input.onChanged().subscribe(value -> stateRef[0] = value);
        inputWrap.child(input);
        LabelComponent suffixLabel = tint(suffix, 0xFF8CA0B6);
        inputWrap.child(suffixLabel);

        field.child(inputWrap);
        row.child(field);
        return new FieldUi(inputWrap, input, suffixLabel);
    }

    private long parseLongField(String raw, long fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private void applyFieldStyle(FieldUi field, FieldStyle style) {
        switch (style) {
            case ACTIVE -> {
                field.container().surface(Surface.flat(0xFF111C28).and(Surface.outline(0xFF7E6CE0)));
                field.suffix().color(Color.ofArgb(0xFF8CA0B6));
            }
            case INVALID -> {
                field.container().surface(Surface.flat(0xFF111C28).and(Surface.outline(0xFFBB5045)));
                field.suffix().color(Color.ofArgb(0xFF8CA0B6));
            }
            case DIMMED -> {
                field.container().surface(Surface.flat(0xFF111C28).and(Surface.outline(0xFF48627D)));
                field.suffix().color(Color.ofArgb(0xFF6A7888));
            }
        }
    }

    private enum FieldStyle {
        ACTIVE,
        INVALID,
        DIMMED
    }

    private record FieldUi(FlowLayout container, TextBoxComponent input, LabelComponent suffix) {
    }

    private void closeListingDialog() {
        if (this.listingDialog != null) {
            this.listingDialog.remove();
            this.listingDialog = null;
        }
    }

    private UUID activeShopId() {
        UUID fromMenu = this.menu.getShopId();
        if (fromMenu.equals(ZERO_UUID)) {
            return ShopClientState.getLastShopId();
        }
        return fromMenu;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.minecraft != null && this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_E) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
