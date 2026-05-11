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
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.ledok.economy_ld.network.packet.s2c.ShopActionResultS2CPacket;
import net.ledok.economy_ld.network.packet.c2s.AddListingC2SPacket;
import net.ledok.economy_ld.network.packet.c2s.BuyItemC2SPacket;
import net.ledok.economy_ld.network.packet.c2s.RemoveListingC2SPacket;
import net.ledok.economy_ld.network.packet.c2s.RestockListingC2SPacket;
import net.ledok.economy_ld.network.packet.c2s.SellItemC2SPacket;
import net.ledok.economy_ld.network.packet.c2s.UpdateListingC2SPacket;
import net.ledok.economy_ld.screen.ShopBrowseScreenHandler;
import net.ledok.economy_ld.shop.ShopListing;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

public class ShopBrowseScreen extends BaseOwoHandledScreen<StackLayout, ShopBrowseScreenHandler> {
    private static final UUID ZERO_UUID = new UUID(0L, 0L);
    // Computed dynamically in build() based on available height
    private int listingsPerPage = 5;
    private boolean compact = false;

    // Layout refs
    private LabelComponent ownerLabel;
    private LabelComponent listingCountLabel;
    private LabelComponent balanceLabel;
    private LabelComponent pageLabel;
    private ButtonComponent prevButton;
    private ButtonComponent nextButton;
    private ButtonComponent newListingButton;
    private ButtonComponent firstListingButton;
    private ButtonComponent sortButton;
    private FlowLayout pageBars;
    private FlowLayout contentArea;
    private FlowLayout middleSection;
    private StackLayout rootLayout;
    private OverlayContainer<FlowLayout> listingDialog;

    // Filter / sort state
    private String searchQuery = "";
    private boolean searchOpen = false;
    private SortMode sortMode = SortMode.NAME;

    // Change tracking
    private int lastTotalPages = -1;
    private int lastCurrentPage = -1;
    private int lastListingCount = -1;
    private String lastSearchQuery = null;
    private SortMode lastSortMode = null;
    private int lastSyncVersion = -1;
    private int lastActionVersion = -1;
    private FlowLayout toastWidget = null;
    private long toastExpiryMs = 0;

    private enum SortMode {
        NAME, BUY_PRICE, SELL_PRICE, STOCK;

        SortMode next() {
            return values()[(ordinal() + 1) % values().length];
        }

        String label() {
            return switch (this) {
                case NAME -> "SORT · NAME";
                case BUY_PRICE -> "SORT · BUY";
                case SELL_PRICE -> "SORT · SELL";
                case STOCK -> "SORT · STOCK";
            };
        }
    }

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

        int shellW = Math.min(this.width - 32, 980);
        int shellH = Math.min(this.height - 32, 520);
        this.compact = shellW < 700 || shellH < 360;

        int headerH = s(64), footerH = s(58), rowH = s(52) + 4, colH = s(28) + 4;
        int reservedH = headerH + footerH + colH + 24;
        this.listingsPerPage = Math.max(2, (shellH - reservedH) / rowH);

        FlowLayout shell = Containers.verticalFlow(Sizing.fixed(shellW), Sizing.fixed(shellH));
        shell.surface(Surface.flat(0xFF0D151F).and(Surface.outline(0xFF2F4155)));
        shell.padding(Insets.of(1));

        FlowLayout panel = Containers.verticalFlow(Sizing.fill(100), Sizing.fill(100));
        panel.surface(Surface.flat(0xFF0B141E));
        panel.child(buildHeader());

        this.middleSection = Containers.verticalFlow(Sizing.fill(100), Sizing.expand());
        this.contentArea = Containers.verticalFlow(Sizing.fill(100), Sizing.expand());
        this.middleSection.child(this.contentArea);
        panel.child(this.middleSection);
        panel.child(buildFooter());

        shell.child(panel);
        mainLayer.child(shell);
        rootComponent.child(mainLayer);

        // Sync version trackers to current state so stale toasts don't fire on open
        this.lastSyncVersion = ShopClientState.getSyncVersion();
        this.lastActionVersion = ShopClientState.getActionVersion();

        refreshUi(true);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // Expire toast
        if (this.toastWidget != null && System.currentTimeMillis() > this.toastExpiryMs) {
            this.toastWidget.remove();
            this.toastWidget = null;
        }
        // Show new toast if action version changed
        int currentActionVersion = ShopClientState.getActionVersion();
        if (currentActionVersion != lastActionVersion) {
            lastActionVersion = currentActionVersion;
            ShopActionResultS2CPacket result = ShopClientState.getLastActionResult();
            if (result != null) showToast(result);
        }
        refreshUi(false);
    }

    // ─── Filter + Sort ───────────────────────────────────────────────────────

    private List<ShopListing> applyFilterAndSort(List<ShopListing> listings) {
        var stream = listings.stream();
        if (!searchQuery.isBlank()) {
            String[] tokens = searchQuery.trim().toLowerCase(Locale.ROOT).split("\\s+");
            stream = stream.filter(l -> {
                for (String token : tokens) {
                    if (token.startsWith("@")) {
                        String ns = token.substring(1);
                        String itemId = l.itemStack().getItem().toString().toLowerCase(Locale.ROOT);
                        String namespace = itemId.contains(":") ? itemId.split(":")[0] : itemId;
                        if (!namespace.contains(ns)) return false;
                    } else if (token.startsWith("#")) {
                        String tagQuery = token.substring(1);
                        boolean tagMatch = l.itemStack().getTags().anyMatch(tag -> {
                            String path = tag.location().getPath().toLowerCase(Locale.ROOT);
                            String full = tag.location().toString().toLowerCase(Locale.ROOT);
                            return path.contains(tagQuery) || full.contains(tagQuery);
                        });
                        if (!tagMatch) return false;
                    } else {
                        String name = l.itemStack().getHoverName().getString().toLowerCase(Locale.ROOT);
                        String id = l.itemStack().getItem().toString().toLowerCase(Locale.ROOT);
                        if (!name.contains(token) && !id.contains(token)) return false;
                    }
                }
                return true;
            });
        }
        stream = switch (sortMode) {
            case NAME -> stream.sorted(Comparator.comparing(l -> l.itemStack().getHoverName().getString()));
            case BUY_PRICE -> stream.sorted(Comparator.comparingLong(l -> l.priceBuy() == null ? Long.MAX_VALUE : l.priceBuy()));
            case SELL_PRICE -> stream.sorted(Comparator.comparingLong(l -> l.priceSell() == null ? Long.MAX_VALUE : l.priceSell()));
            case STOCK -> stream.sorted(Comparator.comparingLong(l -> l.stock() == null ? Long.MAX_VALUE : l.stock()));
        };
        return stream.collect(Collectors.toList());
    }

    private void cycleSortMode() {
        sortMode = sortMode.next();
        if (sortButton != null) sortButton.setMessage(Component.literal(sortMode.label()));
        menu.setPage(0);
        refreshUi(true);
    }

    private void toggleSearch() {
        searchOpen = !searchOpen;
        if (!searchOpen) {
            searchQuery = "";
            menu.setPage(0);
        }
        rebuildMiddle();
        refreshUi(true);
    }

    private void rebuildMiddle() {
        middleSection.clearChildren();
        if (searchOpen) middleSection.child(buildSearchBar());
        middleSection.child(contentArea);
    }

    private FlowLayout buildSearchBar() {
        FlowLayout bar = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(36));
        bar.surface(Surface.flat(0xFF0D1A27).and(Surface.outline(0xFF1F3042)));
        bar.padding(Insets.both(8, 6));
        bar.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        bar.gap(8);
        bar.child(tint("FIND", 0xFF6D8299));
        bar.child(tint(">", 0xFF4A5F78));
        TextBoxComponent field = Components.textBox(Sizing.expand(), searchQuery);
        field.setMaxLength(64);
        field.setSuggestion(searchQuery.isEmpty() ? "name  |  @mod  |  #tag" : "");
        field.onChanged().subscribe(v -> {
            searchQuery = v;
            field.setSuggestion(v.isEmpty() ? "name  |  @mod  |  #tag" : "");
            menu.setPage(0);
            refreshUi(true);
        });
        bar.child(field);
        bar.child(smallButton("✕", 24, 24, 0xFF132131, 0xFF193047, 0xFF334A60, b -> toggleSearch()));
        return bar;
    }

    // ─── Refresh ─────────────────────────────────────────────────────────────

    private void refreshUi(boolean force) {
        if (this.listingDialog != null && !this.listingDialog.hasParent()) {
            this.listingDialog = null;
        }

        UUID shopId = activeShopId();
        List<ShopListing> allListings = ShopClientState.getListings(shopId);
        List<ShopListing> listings = applyFilterAndSort(allListings);
        boolean adminShop = ShopClientState.isAdminShop(shopId);
        boolean canManage = ShopClientState.canManage(shopId);
        String owner = adminShop ? "Server" : ShopClientState.ownerLabel(shopId);
        long balance = ShopClientState.openerBalance(shopId);

        int totalPages = Math.max(1, (listings.size() + listingsPerPage - 1) / listingsPerPage);
        if (this.menu.getPage() >= totalPages) this.menu.setPage(totalPages - 1);
        int currentPage = this.menu.getPage() + 1;
        int currentPageIndex = this.menu.getPage();
        boolean pageChanged = this.lastCurrentPage != currentPage;

        this.ownerLabel.text(Component.literal("OWNER  ·  " + owner));
        String countText = searchQuery.isBlank()
                ? "LISTINGS  ·  " + allListings.size()
                : "LISTINGS  ·  " + listings.size() + " / " + allListings.size();
        this.listingCountLabel.text(Component.literal(countText));
        this.balanceLabel.text(Component.literal("BAL  ·  " + String.format("%,d", balance) + " LC"));
        this.pageLabel.text(Component.literal("PAGE  " + String.format("%02d", currentPage) + " / " + String.format("%02d", totalPages)));
        this.prevButton.active(currentPage > 1);
        this.nextButton.active(currentPage < totalPages);
        this.newListingButton.active(canManage);
        if (this.firstListingButton != null) this.firstListingButton.active(canManage);
        if (this.sortButton != null) this.sortButton.setMessage(Component.literal(sortMode.label()));

        if (force || this.lastTotalPages != totalPages || pageChanged) {
            rebuildPageBars(totalPages, currentPageIndex);
            this.lastTotalPages = totalPages;
            this.lastCurrentPage = currentPage;
        }

        boolean filterChanged = !searchQuery.equals(lastSearchQuery) || sortMode != lastSortMode;
        int currentSyncVersion = ShopClientState.getSyncVersion();
        boolean syncedNewData = currentSyncVersion != lastSyncVersion;
        if (syncedNewData) lastSyncVersion = currentSyncVersion;

        if (force || this.lastListingCount != allListings.size() || pageChanged || filterChanged || syncedNewData) {
            rebuildContent(listings, canManage, adminShop);
            this.lastListingCount = allListings.size();
            this.lastSearchQuery = searchQuery;
            this.lastSortMode = sortMode;
        }
    }

    // ─── Header ──────────────────────────────────────────────────────────────

    private FlowLayout buildHeader() {
        FlowLayout header = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(s(64)));
        header.surface(Surface.flat(0xFF0D1722).and(Surface.outline(0xFF1F3042)));
        header.padding(Insets.of(s(8)));
        header.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        header.gap(s(10));

        FlowLayout icon = Containers.verticalFlow(Sizing.fixed(s(34)), Sizing.fixed(s(34)));
        icon.surface(Surface.flat(0xFF9C7AE8));
        icon.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        icon.child(tint("₴", 0xFF201333));

        FlowLayout title = Containers.verticalFlow(Sizing.expand(), Sizing.content());
        title.gap(1);
        title.child(tint("LeDok's Wares", 0xFFE7EDF5));

        FlowLayout subtitle = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        subtitle.gap(s(6));
        subtitle.child(tag("PLAYER SHOP"));
        this.ownerLabel = tint("OWNER  ·  Unknown", 0xFF8FA1B5);
        this.listingCountLabel = tint("LISTINGS  ·  0", 0xFF8FA1B5);
        this.balanceLabel = tint("BAL  ·  0 LC", 0xFF9D81EA);
        subtitle.child(this.ownerLabel);
        if (!compact) subtitle.child(this.listingCountLabel);
        subtitle.child(this.balanceLabel);
        title.child(subtitle);

        FlowLayout controls = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        controls.gap(s(6));
        controls.child(smallButton("⌕", s(38), s(32), 0xFF132131, 0xFF193047, 0xFF334A60, b -> toggleSearch()));
        this.sortButton = smallButton(sortMode.label(), compact ? 80 : 110, s(32), 0xFF132131, 0xFF193047, 0xFF334A60, b -> cycleSortMode());
        controls.child(this.sortButton);
        this.newListingButton = smallButton(compact ? "+" : "+ NEW LISTING", compact ? s(32) : 132, s(32), 0xFF9A77E8, 0xFFAA8AF0, 0xFFC4AFFF, button -> openItemPicker());
        controls.child(this.newListingButton);
        if (!compact) controls.child(smallButton("⋯", s(38), s(32), 0xFF132131, 0xFF193047, 0xFF334A60));
        controls.child(smallButton("✕", s(32), s(32), 0xFF132131, 0xFF193047, 0xFF334A60, button -> this.onClose()));

        header.child(icon);
        header.child(title);
        header.child(controls);
        return header;
    }

    // ─── Body ────────────────────────────────────────────────────────────────

    private FlowLayout buildEmptyBody() {
        Surface striped = Surface.flat(0xFF111C29).and((context, component) -> {
            int x = component.x(), y = component.y(), w = component.width(), h = component.height();
            for (int row = 0; row < h; row += 18)
                context.fill(x, y + row, x + w, y + row + 9, 0xFF0D1722);
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
        this.firstListingButton = largeCta("+ ADD YOUR FIRST LISTING", button -> openItemPicker());
        body.child(this.firstListingButton);
        return body;
    }

    private void rebuildContent(List<ShopListing> listings, boolean canManage, boolean adminShop) {
        this.contentArea.clearChildren();
        if (listings.isEmpty()) {
            this.contentArea.child(buildEmptyBody());
            return;
        }
        this.contentArea.child(buildListingsBody(listings, canManage, adminShop));
    }

    private FlowLayout buildListingsBody(List<ShopListing> listings, boolean canManage, boolean adminShop) {
        FlowLayout body = Containers.verticalFlow(Sizing.fill(100), Sizing.expand());
        body.surface(Surface.flat(0xFF111C29).and(Surface.outline(0xFF1C2D3F)));
        body.padding(Insets.of(8));
        body.gap(4);

        FlowLayout colHeader = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        colHeader.surface(Surface.flat(0xFF172433));
        colHeader.padding(Insets.of(4));
        colHeader.gap(8);
        colHeader.child(cellLabel("ITEM", 250, 0xFF6D8299));
        colHeader.child(cellLabel("BUY", 80, 0xFF6D8299));
        colHeader.child(cellLabel("SELL", 80, 0xFF6D8299));
        colHeader.child(cellLabel("STOCK", 90, 0xFF6D8299));
        colHeader.child(Containers.horizontalFlow(Sizing.expand(), Sizing.content()));
        colHeader.child(cellLabelRight("ACTIONS", canManage ? 210 : 130, 0xFF6D8299));
        body.child(colHeader);

        int start = this.menu.getPage() * listingsPerPage;
        int end = Math.min(start + listingsPerPage, listings.size());
        for (int i = start; i < end; i++)
            body.child(buildListingRow(listings.get(i), i, canManage, adminShop));

        return body;
    }

    private FlowLayout buildListingRow(ShopListing listing, int index, boolean canManage, boolean adminShop) {
        int bg = (index % 2 == 0) ? 0xFF1A2A3A : 0xFF1C2D40;
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(s(52)));
        row.surface(Surface.flat(bg));
        row.padding(Insets.of(s(4)));
        row.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        row.gap(s(8));

        int itemCellW = compact ? 160 : 250;
        FlowLayout itemCell = Containers.horizontalFlow(Sizing.fixed(itemCellW), Sizing.content());
        itemCell.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        itemCell.gap(s(6));
        itemCell.child(Components.item(listing.itemStack().copyWithCount(1)).showOverlay(true).setTooltipFromStack(true));
        FlowLayout itemText = Containers.verticalFlow(Sizing.expand(), Sizing.content());
        itemText.gap(1);
        itemText.child(tint(listing.itemStack().getHoverName().getString(), 0xFFE4EBF4));
        if (!compact) itemText.child(tint(listing.itemStack().getItem().toString(), 0xFF6F849A));
        itemCell.child(itemText);
        row.child(itemCell);

        int priceW = compact ? 60 : 80;
        row.child(priceCell(listing.priceBuy(), listing.perOp(), 0xFF8EDB84, priceW));
        row.child(priceCell(listing.priceSell(), listing.perOp(), 0xFFB899F2, priceW));

        String stockText = adminShop ? "∞" : (listing.stock() == null ? "∞" : String.format("%,d", listing.stock()));
        int stockColor = adminShop ? 0xFFB899F2 : 0xFFD0DCEC;
        row.child(cellLabel(stockText, compact ? 50 : 90, stockColor));

        row.child(Containers.horizontalFlow(Sizing.expand(), Sizing.content()));

        FlowLayout actions = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        actions.alignment(HorizontalAlignment.RIGHT, VerticalAlignment.CENTER);
        actions.gap(s(6));

        if (canManage) {
            actions.child(smallButton("EDIT", s(56), s(24), 0xFF1B2A3B, 0xFF22354A, 0xFF3B526A, b -> openEditDialog(listing)));
            if (!adminShop) {
                actions.child(smallButton("RESTOCK", s(70), s(24), 0xFF1B2A3B, 0xFF22354A, 0xFF3B526A, b -> openRestockDialog(listing)));
            }
            actions.child(smallButton("REMOVE", s(62), s(24), 0xFF2B1B1B, 0xFF3B2323, 0xFF7A3F3F, b -> openRemoveConfirmDialog(listing)));
        } else {
            if (listing.priceBuy() != null)
                actions.child(smallButton("BUY", s(56), s(24), 0xFF1A2E1A, 0xFF244A24, 0xFF3A7A3A, b -> ClientPlayNetworking.send(new BuyItemC2SPacket(listing.id(), listing.perOp()))));
            if (listing.priceSell() != null)
                actions.child(smallButton("SELL", s(56), s(24), 0xFF1E1A2E, 0xFF2A2244, 0xFF6A52B8, b -> ClientPlayNetworking.send(new SellItemC2SPacket(listing.id(), listing.perOp()))));
            if (listing.priceBuy() == null && listing.priceSell() == null)
                actions.child(tint("–", 0xFF4A5F78));
        }

        row.child(actions);
        return row;
    }

    // ─── Footer ──────────────────────────────────────────────────────────────

    private FlowLayout buildFooter() {
        FlowLayout footer = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(s(58)));
        footer.surface(Surface.flat(0xFF0B141E).and(Surface.outline(0xFF1F3042)));
        footer.padding(Insets.of(s(12)));
        footer.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        this.prevButton = smallButton("◀ PREV", s(78), s(30), 0xFF1A2736, 0xFF223347, 0xFF3A4F67);
        this.prevButton.onPress(b -> { this.menu.setPage(Math.max(0, this.menu.getPage() - 1)); refreshUi(true); });

        this.nextButton = smallButton("NEXT ▶", s(78), s(30), 0xFF1A2736, 0xFF223347, 0xFF3A4F67);
        this.nextButton.onPress(b -> { this.menu.setPage(this.menu.getPage() + 1); refreshUi(true); });

        FlowLayout center = Containers.horizontalFlow(Sizing.expand(), Sizing.content());
        center.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        center.gap(10);

        this.pageBars = Containers.horizontalFlow(Sizing.fixed(150), Sizing.fixed(4));
        this.pageBars.gap(2);
        this.pageLabel = tint("PAGE  01 / 01", 0xFFD0DBE7);
        center.child(this.pageBars);
        center.child(this.pageLabel);

        footer.child(this.prevButton);
        footer.child(center);
        footer.child(this.nextButton);
        return footer;
    }

    private void rebuildPageBars(int totalPages, int currentPageIndex) {
        this.pageBars.clearChildren();
        final int containerWidth = 150, gap = 2;
        int usableWidth = Math.max(totalPages, containerWidth - gap * Math.max(0, totalPages - 1));
        int baseWidth = usableWidth / totalPages, remainder = usableWidth % totalPages;
        for (int i = 0; i < totalPages; i++) {
            BoxComponent bar = Components.box(Sizing.fixed(baseWidth + (i < remainder ? 1 : 0)), Sizing.fill(100));
            bar.fill(true);
            bar.color(i == currentPageIndex ? Color.ofRgb(0xA875FF) : Color.ofRgb(0x3B4D61));
            this.pageBars.child(bar);
        }
    }

    // ─── Restock Dialog ──────────────────────────────────────────────────────

    private void openRestockDialog(ShopListing listing) {
        if (this.listingDialog != null) return;

        int inInventory = 0;
        if (this.minecraft != null && this.minecraft.player != null) {
            for (int i = 0; i < this.minecraft.player.getInventory().getContainerSize(); i++) {
                ItemStack s = this.minecraft.player.getInventory().getItem(i);
                if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, listing.itemStack()))
                    inInventory += s.getCount();
            }
        }
        final int maxRestock = inInventory;
        final String[] qtyValue = {"64"};

        FlowLayout panel = Containers.verticalFlow(Sizing.fixed(460), Sizing.content());
        panel.surface(Surface.flat(0xFF111C28).and(Surface.outline(0xFF4E6177)));
        panel.padding(Insets.of(14));
        panel.gap(10);

        // Header
        FlowLayout head = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        head.gap(3);
        head.child(tint("Restock", 0xFFE6EDF7));
        head.child(tint("MOVE ITEMS FROM INVENTORY INTO SHOP STOCK", 0xFF6D8299));

        // Body
        FlowLayout body = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        body.surface(Surface.flat(0xFF1A2A3A).and(Surface.outline(0xFF334A60)));
        body.padding(Insets.of(12));
        body.gap(12);

        // Item card
        FlowLayout itemCard = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        itemCard.surface(Surface.flat(0xFF202E3F).and(Surface.outline(0xFF3B526B)));
        itemCard.padding(Insets.of(12));
        itemCard.gap(10);
        itemCard.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        itemCard.child(Components.item(listing.itemStack().copyWithCount(1)).showOverlay(true).setTooltipFromStack(true));
        FlowLayout itemCardText = Containers.verticalFlow(Sizing.expand(), Sizing.content());
        itemCardText.gap(2);
        itemCardText.child(tint(listing.itemStack().getHoverName().getString(), 0xFFE6EDF7));
        String stockLabel = listing.stock() == null ? "CURRENT STOCK  ·  ∞" : "CURRENT STOCK  ·  " + listing.stock();
        itemCardText.child(tint(stockLabel, 0xFF8DA2B8));
        itemCard.child(itemCardText);
        FlowLayout invSection = Containers.verticalFlow(Sizing.content(), Sizing.content());
        invSection.alignment(HorizontalAlignment.RIGHT, VerticalAlignment.CENTER);
        invSection.gap(2);
        invSection.child(tint("IN INVENTORY", 0xFF6D8299));
        LabelComponent invLabel = tint(String.valueOf(maxRestock), 0xFFE6EDF7);
        invSection.child(invLabel);
        itemCard.child(invSection);
        body.child(itemCard);

        // Qty field
        FlowLayout fieldSection = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        fieldSection.gap(6);
        FlowLayout fieldHead = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        fieldHead.child(tint("ADD TO STOCK", 0xFF9CB2C8));
        fieldHead.child(Containers.horizontalFlow(Sizing.expand(), Sizing.content()));
        fieldHead.child(tint("Items will be taken from your inventory", 0xFF6D8299));
        fieldSection.child(fieldHead);

        FlowLayout inputWrap = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(40));
        inputWrap.surface(Surface.flat(0xFF111C28).and(Surface.outline(0xFF7E6CE0)));
        inputWrap.padding(Insets.of(6));
        inputWrap.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        inputWrap.gap(6);
        TextBoxComponent qtyInput = Components.textBox(Sizing.expand(), "64");
        qtyInput.setMaxLength(8);
        qtyInput.onChanged().subscribe(v -> qtyValue[0] = v);
        inputWrap.child(qtyInput);
        inputWrap.child(tint("LC", 0xFF8CA0B6));
        fieldSection.child(inputWrap);

        // Quick chips
        FlowLayout chips = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        chips.gap(6);
        for (int n : new int[]{16, 32, 64, 128}) {
            final int val = n;
            chips.child(smallButton("+" + n, 62, 28, 0xFF1B2A3B, 0xFF22354A, 0xFF3B526A, b -> {
                int cur = (int) parseLongField(qtyValue[0], 0);
                qtyInput.text(String.valueOf(Math.min(maxRestock, cur + val)));
            }));
        }
        String allLabel = "ALL (" + maxRestock + ")";
        chips.child(smallButton(allLabel, Math.max(80, allLabel.length() * 6 + 16), 28, 0xFF1B2A3B, 0xFF22354A, 0xFF3B526A,
                b -> qtyInput.text(String.valueOf(maxRestock))));
        fieldSection.child(chips);
        body.child(fieldSection);

        // Actions
        ButtonComponent addBtn = Components.button(Component.literal("+ ADD 64"), b -> {
            long qty = Math.min(maxRestock, Math.max(1, parseLongField(qtyValue[0], 0)));
            if (qty > 0) ClientPlayNetworking.send(new RestockListingC2SPacket(listing.id(), (int) qty));
            closeListingDialog();
        });
        addBtn.sizing(Sizing.fixed(120), Sizing.fixed(32));
        addBtn.renderer((context, rendered, delta) -> {
            int col = rendered.isHoveredOrFocused() ? 0xFFAA8AF0 : 0xFF9A77E8;
            context.fill(rendered.getX(), rendered.getY(), rendered.getX() + rendered.getWidth(), rendered.getY() + rendered.getHeight(), col);
            context.drawRectOutline(rendered.getX(), rendered.getY(), rendered.getWidth(), rendered.getHeight(), 0xFFC4AFFF);
        });
        qtyInput.onChanged().subscribe(v -> {
            long qty = Math.min(maxRestock, Math.max(0, parseLongField(v, 0)));
            addBtn.setMessage(Component.literal("+ ADD " + qty));
        });

        FlowLayout actions = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        actions.alignment(HorizontalAlignment.RIGHT, VerticalAlignment.CENTER);
        actions.gap(6);
        actions.child(smallButton("CANCEL", 98, 32, 0xFF1B2A3B, 0xFF22354A, 0xFF3B526A, b -> closeListingDialog()));
        actions.child(addBtn);

        panel.child(head);
        panel.child(body);
        panel.child(actions);

        this.listingDialog = Containers.overlay(panel);
        this.listingDialog.closeOnClick(false);
        this.listingDialog.surface(Surface.flat(0x88000000));
        this.listingDialog.zIndex(300);
        this.rootLayout.child(this.listingDialog);
    }

    // ─── Edit Dialog ─────────────────────────────────────────────────────────

    private void openEditDialog(ShopListing listing) {
        if (this.listingDialog != null) return;

        final String[] buyValue = {listing.priceBuy() == null ? "0" : String.valueOf(listing.priceBuy())};
        final String[] sellValue = {listing.priceSell() == null ? "0" : String.valueOf(listing.priceSell())};
        final String[] perOpValue = {String.valueOf(listing.perOp())};
        final String[] buyCapValue = {listing.buyCap() == null ? "0" : String.valueOf(listing.buyCap())};

        FlowLayout panel = Containers.verticalFlow(Sizing.fixed(500), Sizing.content());
        panel.surface(Surface.flat(0xFF111C28).and(Surface.outline(0xFF4E6177)));
        panel.padding(Insets.of(10));
        panel.gap(8);

        FlowLayout head = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        head.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        FlowLayout titlePack = Containers.verticalFlow(Sizing.expand(), Sizing.content());
        titlePack.gap(1);
        titlePack.child(tint("Edit Listing", 0xFFE6EDF7));
        titlePack.child(tint(listing.itemStack().getItem().toString().toUpperCase(), 0xFF7E93AA));
        head.child(titlePack);
        head.child(Containers.horizontalFlow(Sizing.expand(), Sizing.content()));
        head.child(smallButton("✕", 28, 28, 0xFF132131, 0xFF193047, 0xFF334A60, b -> closeListingDialog()));

        FlowLayout body = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        body.surface(Surface.flat(0xFF1A2A3A).and(Surface.outline(0xFF334A60)));
        body.padding(Insets.of(12));
        body.gap(10);

        FlowLayout itemCard = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        itemCard.surface(Surface.flat(0xFF202E3F).and(Surface.outline(0xFF3B526B)));
        itemCard.padding(Insets.of(12));
        itemCard.gap(10);
        itemCard.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        itemCard.child(Components.item(listing.itemStack().copyWithCount(1)).showOverlay(true).setTooltipFromStack(true));
        FlowLayout itemCardText = Containers.verticalFlow(Sizing.expand(), Sizing.content());
        itemCardText.gap(2);
        itemCardText.child(tint(listing.itemStack().getHoverName().getString(), 0xFFE6EDF7));
        String stockLabel = listing.stock() == null ? "∞  IN STOCK" : "x" + listing.stock() + "  IN STOCK";
        itemCardText.child(tint(stockLabel, 0xFF8DA2B8));
        itemCard.child(itemCardText);
        body.child(itemCard);

        LabelComponent previewLine1 = tint("", 0xFF91C982);
        LabelComponent previewLine2 = tint("", 0xFFA88FE7);

        FlowLayout row1 = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row1.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        row1.gap(12);
        FieldUi buyField = createDialogField(row1, "BUY PRICE", "player buys from shop", buyValue[0], "LC", buyValue);
        FieldUi sellField = createDialogField(row1, "SELL PRICE", "shop buys from player", sellValue[0], "LC", sellValue);
        body.child(row1);

        FlowLayout row2 = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row2.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        row2.gap(12);
        FieldUi perOpField = createDialogField(row2, "ITEMS PER OP", "per buy / sell click", perOpValue[0], "x", perOpValue);
        FieldUi buyCapField = createDialogField(row2, "BUY CAP", "max stock from players", buyCapValue[0], "max", buyCapValue);
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
            long buy = parseLongField(buyValue[0], 0), sell = parseLongField(sellValue[0], 0);
            long perOp = Math.max(1, parseLongField(perOpValue[0], 1)), buyCap = Math.max(0, parseLongField(buyCapValue[0], 0));
            boolean valid = buy > 0 || sell > 0;
            applyFieldStyle(buyField, buy > 0 ? FieldStyle.ACTIVE : FieldStyle.INVALID);
            applyFieldStyle(sellField, sell > 0 ? FieldStyle.ACTIVE : FieldStyle.INVALID);
            applyFieldStyle(perOpField, FieldStyle.ACTIVE);
            applyFieldStyle(buyCapField, sell > 0 ? FieldStyle.ACTIVE : FieldStyle.DIMMED);
            previewLine1.text(Component.literal(buy > 0 ? "→ PLAYER BUYS ×" + perOp + " FOR " + String.format("%,d", buy) + " LC" : ""));
            if (sell > 0) {
                previewLine2.text(Component.literal("← SHOP BUYS ×" + perOp + " FOR " + String.format("%,d", sell) + " LC" + (buyCap > 0 ? " UP TO " + buyCap : "")));
            } else {
                previewLine2.text(Component.literal(""));
            }
            errorBanner.surface(valid ? Surface.flat(0x00000000) : Surface.flat(0xFF5A231D).and(Surface.outline(0xFF9B3F33)));
            errorText.text(Component.literal(valid ? "" : "⚠ SET AT LEAST ONE PRICE."));
        };
        buyField.input().onChanged().subscribe(v -> refreshPreview.run());
        sellField.input().onChanged().subscribe(v -> refreshPreview.run());
        perOpField.input().onChanged().subscribe(v -> refreshPreview.run());
        buyCapField.input().onChanged().subscribe(v -> refreshPreview.run());
        refreshPreview.run();

        FlowLayout actions = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        actions.alignment(HorizontalAlignment.RIGHT, VerticalAlignment.CENTER);
        actions.gap(6);
        actions.child(smallButton("CANCEL", 98, 32, 0xFF1B2A3B, 0xFF22354A, 0xFF3B526A, b -> closeListingDialog()));
        actions.child(smallButton("CONFIRM", 108, 32, 0xFF9A77E8, 0xFFAA8AF0, 0xFFC4AFFF, b -> {
            long buy = parseLongField(buyValue[0], 0), sell = parseLongField(sellValue[0], 0);
            if (buy <= 0 && sell <= 0) {
                errorBanner.surface(Surface.flat(0xFF5A231D).and(Surface.outline(0xFF9B3F33)));
                errorText.text(Component.literal("⚠ SET AT LEAST ONE PRICE."));
                return;
            }
            long perOp = Math.max(1, parseLongField(perOpValue[0], 1));
            long buyCap = Math.max(0, parseLongField(buyCapValue[0], 0));
            ClientPlayNetworking.send(new UpdateListingC2SPacket(listing.id(), buy > 0 ? buy : null, sell > 0 ? sell : null,
                    (int) Math.min(Integer.MAX_VALUE, perOp), buyCap > 0 ? buyCap : null));
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

    // ─── New Listing Dialog ───────────────────────────────────────────────────

    // ─── Item Picker ─────────────────────────────────────────────────────────

    private void openItemPicker() {
        if (this.listingDialog != null) return;
        if (this.minecraft == null || this.minecraft.player == null) return;

        // Collect unique non-empty stacks from player inventory
        java.util.List<ItemStack> slots = new java.util.ArrayList<>();
        var inv = this.minecraft.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty()) slots.add(s);
        }

        FlowLayout panel = Containers.verticalFlow(Sizing.fixed(480), Sizing.content());
        panel.surface(Surface.flat(0xFF111C28).and(Surface.outline(0xFF4E6177)));
        panel.padding(Insets.of(14));
        panel.gap(10);

        // Header
        FlowLayout head = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        head.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        FlowLayout titlePack = Containers.verticalFlow(Sizing.expand(), Sizing.content());
        titlePack.gap(2);
        titlePack.child(tint("New Listing", 0xFFE6EDF7));
        titlePack.child(tint("SELECT AN ITEM FROM YOUR INVENTORY", 0xFF6D8299));
        head.child(titlePack);
        head.child(smallButton("✕", 28, 28, 0xFF132131, 0xFF193047, 0xFF334A60, b -> closeListingDialog()));
        panel.child(head);

        // Grid body
        FlowLayout body = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        body.surface(Surface.flat(0xFF1A2A3A).and(Surface.outline(0xFF334A60)));
        body.padding(Insets.of(12));
        body.gap(4);

        if (slots.isEmpty()) {
            FlowLayout empty = Containers.verticalFlow(Sizing.fill(100), Sizing.fixed(80));
            empty.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
            empty.child(tint("Your inventory is empty.", 0xFF6D8299));
            body.child(empty);
        } else {
            // Rows of 9 slots each
            final int COLS = 9;
            for (int row = 0; row < Math.ceil(slots.size() / (double) COLS); row++) {
                FlowLayout rowLayout = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
                rowLayout.gap(4);
                for (int col = 0; col < COLS; col++) {
                    int idx = row * COLS + col;
                    if (idx >= slots.size()) {
                        // Empty placeholder to keep grid aligned
                        FlowLayout empty = Containers.verticalFlow(Sizing.fixed(40), Sizing.fixed(40));
                        empty.surface(Surface.flat(0xFF131E2B).and(Surface.outline(0xFF1E2F40)));
                        rowLayout.child(empty);
                    } else {
                        ItemStack stack = slots.get(idx);
                        FlowLayout slot = Containers.verticalFlow(Sizing.fixed(40), Sizing.fixed(40));
                        slot.surface(Surface.flat(0xFF1E2E3E).and(Surface.outline(0xFF334A60)));
                        slot.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
                        slot.child(Components.item(stack).showOverlay(true).setTooltipFromStack(true));
                        // Hover highlight via button overlay trick
                        ButtonComponent slotBtn = Components.button(Component.empty(), b -> {
                            closeListingDialog();
                            openPriceDialog(stack.copyWithCount(1));
                        });
                        slotBtn.sizing(Sizing.fixed(40), Sizing.fixed(40));
                        slotBtn.renderer((ctx, rendered, delta) -> {
                            if (rendered.isHoveredOrFocused()) {
                                ctx.fill(rendered.getX(), rendered.getY(),
                                        rendered.getX() + rendered.getWidth(),
                                        rendered.getY() + rendered.getHeight(),
                                        0x449C7AE8);
                                ctx.drawRectOutline(rendered.getX(), rendered.getY(),
                                        rendered.getWidth(), rendered.getHeight(), 0xFFA98BE8);
                            }
                        });
                        StackLayout slotStack = Containers.stack(Sizing.fixed(40), Sizing.fixed(40));
                        slotStack.child(slot);
                        slotStack.child(slotBtn);
                        rowLayout.child(slotStack);
                    }
                }
                body.child(rowLayout);
            }
        }
        panel.child(body);

        // Footer
        FlowLayout actions = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        actions.alignment(HorizontalAlignment.RIGHT, VerticalAlignment.CENTER);
        actions.child(smallButton("CANCEL", 98, 32, 0xFF1B2A3B, 0xFF22354A, 0xFF3B526A, b -> closeListingDialog()));
        panel.child(actions);

        this.listingDialog = Containers.overlay(panel);
        this.listingDialog.closeOnClick(false);
        this.listingDialog.surface(Surface.flat(0x88000000));
        this.listingDialog.zIndex(300);
        this.rootLayout.child(this.listingDialog);
    }

    private void openPriceDialog(ItemStack selectedStack) {
        if (this.listingDialog != null) return;

        final String[] buyValue = {"0"}, sellValue = {"0"}, perOpValue = {"1"}, buyCapValue = {"256"};

        FlowLayout panel = Containers.verticalFlow(Sizing.fixed(500), Sizing.content());
        panel.surface(Surface.flat(0xFF111C28).and(Surface.outline(0xFF4E6177)));
        panel.padding(Insets.of(10));
        panel.gap(8);

        FlowLayout head = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        head.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        FlowLayout titlePack = Containers.verticalFlow(Sizing.expand(), Sizing.content());
        titlePack.gap(1);
        titlePack.child(tint("New Listing", 0xFFE6EDF7));
        titlePack.child(tint(selectedStack.isEmpty() ? "EMPTY" : selectedStack.getItem().toString().toUpperCase(), 0xFF7E93AA));
        head.child(titlePack);
        head.child(Containers.horizontalFlow(Sizing.expand(), Sizing.content()));
        head.child(smallButton("✕", 28, 28, 0xFF132131, 0xFF193047, 0xFF334A60, b -> closeListingDialog()));

        FlowLayout body = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        body.surface(Surface.flat(0xFF1A2A3A).and(Surface.outline(0xFF334A60)));
        body.padding(Insets.of(12));
        body.gap(10);

        FlowLayout itemCard = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        itemCard.surface(Surface.flat(0xFF202E3F).and(Surface.outline(0xFF3B526B)));
        itemCard.padding(Insets.of(12));
        itemCard.gap(10);
        itemCard.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        itemCard.child(Components.item(selectedStack).showOverlay(true).setTooltipFromStack(true));
        FlowLayout itemCardText = Containers.verticalFlow(Sizing.expand(), Sizing.content());
        itemCardText.gap(2);
        itemCardText.child(tint(selectedStack.isEmpty() ? "No Item Selected" : selectedStack.getHoverName().getString(), 0xFFE6EDF7));
        itemCardText.child(tint("x0  IN STOCK", 0xFF8DA2B8));
        itemCard.child(itemCardText);
        // Back button — returns to item picker
        ButtonComponent backBtn = smallButton("◀ BACK", 72, 32, 0xFF132131, 0xFF193047, 0xFF334A60, b -> {
            closeListingDialog();
            openItemPicker();
        });
        itemCard.child(backBtn);
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
            long buy = parseLongField(buyValue[0], 0), sell = parseLongField(sellValue[0], 0);
            long perOp = Math.max(1, parseLongField(perOpValue[0], 1)), buyCap = Math.max(0, parseLongField(buyCapValue[0], 0));
            boolean valid = buy > 0 || sell > 0;
            applyFieldStyle(buyField, buy > 0 ? FieldStyle.ACTIVE : FieldStyle.INVALID);
            applyFieldStyle(sellField, sell > 0 ? FieldStyle.ACTIVE : FieldStyle.INVALID);
            applyFieldStyle(perOpField, FieldStyle.ACTIVE);
            applyFieldStyle(buyCapField, sell > 0 ? FieldStyle.ACTIVE : FieldStyle.DIMMED);
            previewLine1.text(Component.literal(buy > 0 ? "→ PLAYER BUYS ×" + perOp + " FOR " + String.format("%,d", buy) + " LC" : ""));
            if (sell > 0) {
                previewLine2.text(Component.literal("← SHOP BUYS ×" + perOp + " FOR " + String.format("%,d", sell) + " LC" + (buyCap > 0 ? " UP TO " + buyCap : "")));
            } else {
                previewLine2.text(Component.literal(""));
            }
            errorBanner.surface(valid ? Surface.flat(0x00000000) : Surface.flat(0xFF5A231D).and(Surface.outline(0xFF9B3F33)));
            errorText.text(Component.literal(valid ? "" : "⚠ SET AT LEAST ONE PRICE."));
        };
        buyField.input().onChanged().subscribe(v -> refreshPreview.run());
        sellField.input().onChanged().subscribe(v -> refreshPreview.run());
        perOpField.input().onChanged().subscribe(v -> refreshPreview.run());
        buyCapField.input().onChanged().subscribe(v -> refreshPreview.run());
        refreshPreview.run();

        FlowLayout actions = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        actions.alignment(HorizontalAlignment.RIGHT, VerticalAlignment.CENTER);
        actions.gap(6);
        actions.child(smallButton("CANCEL", 98, 32, 0xFF1B2A3B, 0xFF22354A, 0xFF3B526A, b -> closeListingDialog()));
        actions.child(smallButton("CONFIRM", 108, 32, 0xFF9A77E8, 0xFFAA8AF0, 0xFFC4AFFF, b -> {
            long buy = parseLongField(buyValue[0], 0), sell = parseLongField(sellValue[0], 0);
            if (buy <= 0 && sell <= 0) { errorBanner.surface(Surface.flat(0xFF5A231D).and(Surface.outline(0xFF9B3F33))); errorText.text(Component.literal("⚠ SET AT LEAST ONE PRICE.")); return; }
            if (selectedStack.isEmpty()) { errorBanner.surface(Surface.flat(0xFF5A231D).and(Surface.outline(0xFF9B3F33))); errorText.text(Component.literal("⚠ NO ITEM SELECTED.")); return; }
            if (this.minecraft == null || this.minecraft.level == null) return;
            long perOp = Math.max(1, parseLongField(perOpValue[0], 1));
            long buyCap = Math.max(0, parseLongField(buyCapValue[0], 0));
            if (!(selectedStack.saveOptional(this.minecraft.level.registryAccess()) instanceof CompoundTag itemNbt)) { errorBanner.surface(Surface.flat(0xFF5A231D).and(Surface.outline(0xFF9B3F33))); errorText.text(Component.literal("⚠ FAILED TO SERIALIZE ITEM.")); return; }
            ClientPlayNetworking.send(new AddListingC2SPacket(activeShopId(), itemNbt, buy > 0 ? buy : null, sell > 0 ? sell : null, (int) Math.min(Integer.MAX_VALUE, perOp), buyCap > 0 ? buyCap : null));
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

    // ─── Shared helpers ───────────────────────────────────────────────────────

    /** Scale a fixed size down in compact mode (GUI scale 3–4). */
    private int s(int v) { return compact ? Math.max(v * 2 / 3, 12) : v; }

    private FieldUi createDialogField(FlowLayout row, String title, String hint, String initial, String suffix, String[] stateRef) {
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
        input.onChanged().subscribe(v -> stateRef[0] = v);
        inputWrap.child(input);
        LabelComponent suffixLabel = tint(suffix, 0xFF8CA0B6);
        inputWrap.child(suffixLabel);
        field.child(inputWrap);
        row.child(field);
        return new FieldUi(inputWrap, input, suffixLabel);
    }

    private long parseLongField(String raw, long fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try { return Long.parseLong(raw.trim()); } catch (NumberFormatException ignored) { return fallback; }
    }

    private void applyFieldStyle(FieldUi field, FieldStyle style) {
        switch (style) {
            case ACTIVE  -> { field.container().surface(Surface.flat(0xFF111C28).and(Surface.outline(0xFF7E6CE0))); field.suffix().color(Color.ofArgb(0xFF8CA0B6)); }
            case INVALID -> { field.container().surface(Surface.flat(0xFF111C28).and(Surface.outline(0xFFBB5045))); field.suffix().color(Color.ofArgb(0xFF8CA0B6)); }
            case DIMMED  -> { field.container().surface(Surface.flat(0xFF111C28).and(Surface.outline(0xFF48627D))); field.suffix().color(Color.ofArgb(0xFF6A7888)); }
        }
    }

    private enum FieldStyle { ACTIVE, INVALID, DIMMED }
    private record FieldUi(FlowLayout container, TextBoxComponent input, LabelComponent suffix) {}

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

    private FlowLayout priceCell(Long price, int perOp, int activeColor, int width) {
        FlowLayout cell = Containers.verticalFlow(Sizing.fixed(width), Sizing.content());
        cell.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        cell.gap(1);
        if (price == null) {
            cell.child(tint("–", 0xFF4A5F78));
        } else {
            cell.child(tint(String.format("%,d", price), activeColor));
            String sub = perOp > 1 ? "LC · ×" + perOp : "LC";
            cell.child(tint(sub, 0xFF5A7090));
        }
        return cell;
    }

    private LabelComponent cellLabel(String text, int width, int color) {
        LabelComponent label = tint(text, color);
        label.horizontalSizing(Sizing.fixed(width));
        label.maxWidth(width);
        return label;
    }

    private LabelComponent cellLabelRight(String text, int width, int color) {
        LabelComponent label = tint(text, color);
        label.horizontalSizing(Sizing.fixed(width));
        label.maxWidth(width);
        label.horizontalTextAlignment(HorizontalAlignment.RIGHT);
        return label;
    }

    // ─── Remove Confirm Dialog ────────────────────────────────────────────────

    private void openRemoveConfirmDialog(ShopListing listing) {
        if (this.listingDialog != null) return;

        FlowLayout panel = Containers.verticalFlow(Sizing.fixed(400), Sizing.content());
        panel.surface(Surface.flat(0xFF111C28).and(Surface.outline(0xFF4E3030)));
        panel.padding(Insets.of(0));
        panel.gap(0);

        // Header — red left border
        FlowLayout head = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        head.surface(Surface.flat(0xFF1A1010).and((context, component) ->
                context.fill(component.x(), component.y(), component.x() + 3, component.y() + component.height(), 0xFFE05050)));
        head.padding(Insets.of(14, 12, 10, 16));
        head.gap(3);
        head.child(tint("Remove Listing", 0xFFE85050));
        head.child(tint("STOCK WILL RETURN TO YOUR STORAGE", 0xFF8A6060));
        panel.child(head);

        // Item card
        FlowLayout body = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        body.padding(Insets.of(12));
        FlowLayout itemCard = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        itemCard.surface(Surface.flat(0xFF1E2A38).and(Surface.outline(0xFF2E3F54)));
        itemCard.padding(Insets.of(12));
        itemCard.gap(10);
        itemCard.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        itemCard.child(Components.item(listing.itemStack().copyWithCount(1)).showOverlay(true).setTooltipFromStack(true));
        FlowLayout itemText = Containers.verticalFlow(Sizing.expand(), Sizing.content());
        itemText.gap(3);
        itemText.child(tint(listing.itemStack().getHoverName().getString(), 0xFFE6EDF7));

        // Summary line: "3 stock · 220 buy · 90 sell"
        StringBuilder summary = new StringBuilder();
        if (listing.stock() != null) summary.append(listing.stock()).append(" stock");
        else summary.append("∞ stock");
        if (listing.priceBuy() != null) summary.append("  ·  ").append(String.format("%,d", listing.priceBuy())).append(" buy");
        if (listing.priceSell() != null) summary.append("  ·  ").append(String.format("%,d", listing.priceSell())).append(" sell");
        itemText.child(tint(summary.toString(), 0xFF7A8FA8));
        itemCard.child(itemText);
        body.child(itemCard);
        panel.child(body);

        // Footer actions
        FlowLayout actions = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        actions.surface(Surface.flat(0xFF0E1820));
        actions.padding(Insets.of(10, 12, 10, 12));
        actions.alignment(HorizontalAlignment.RIGHT, VerticalAlignment.CENTER);
        actions.gap(8);
        actions.child(smallButton("CANCEL", 90, 32, 0xFF1B2A3B, 0xFF22354A, 0xFF3B526A, b -> closeListingDialog()));

        // Red REMOVE button
        ButtonComponent removeBtn = Components.button(Component.literal("REMOVE"), b -> {
            ClientPlayNetworking.send(new RemoveListingC2SPacket(listing.id()));
            closeListingDialog();
        });
        removeBtn.sizing(Sizing.fixed(90), Sizing.fixed(32));
        removeBtn.renderer((ctx, rendered, delta) -> {
            int bg = rendered.isHoveredOrFocused() ? 0xFFB83A2A : 0xFF9A2A1C;
            ctx.fill(rendered.getX(), rendered.getY(), rendered.getX() + rendered.getWidth(), rendered.getY() + rendered.getHeight(), bg);
            ctx.drawRectOutline(rendered.getX(), rendered.getY(), rendered.getWidth(), rendered.getHeight(), 0xFFD04040);
        });
        actions.child(removeBtn);
        panel.child(actions);

        this.listingDialog = Containers.overlay(panel);
        this.listingDialog.closeOnClick(false);
        this.listingDialog.surface(Surface.flat(0x88000000));
        this.listingDialog.zIndex(300);
        this.rootLayout.child(this.listingDialog);
    }

    // ─── Toast ────────────────────────────────────────────────────────────────

    private void showToast(ShopActionResultS2CPacket result) {
        if (this.toastWidget != null) {
            this.toastWidget.remove();
            this.toastWidget = null;
        }

        int sideColor, titleColor;
        String title, body;

        switch (result.actionType()) {
            case BOUGHT -> {
                sideColor = 0xFF5FC76C; titleColor = 0xFF8EDB84;
                title = "PURCHASE CONFIRMED";
                body = "Bought " + result.quantity() + " × " + result.itemName() + " for " + String.format("%,d", result.lcAmount()) + " LC";
            }
            case SOLD -> {
                sideColor = 0xFF9C7AE8; titleColor = 0xFFB899F2;
                title = "SALE CONFIRMED";
                body = "Sold " + result.quantity() + " × " + result.itemName() + " for " + String.format("%,d", result.lcAmount()) + " LC";
            }
            case RESTOCKED -> {
                sideColor = 0xFF5FC76C; titleColor = 0xFF8EDB84;
                title = "RESTOCKED";
                body = "Added " + result.quantity() + " × " + result.itemName() + " to stock";
            }
            case INSUFFICIENT_FUNDS -> {
                sideColor = 0xFFE05050; titleColor = 0xFFE88080;
                title = "INSUFFICIENT FUNDS";
                body = "You need " + String.format("%,d", result.lcAmount()) + " LC. You have " + String.format("%,d", result.playerBalance()) + " LC.";
            }
            case OUT_OF_STOCK -> {
                sideColor = 0xFFE05050; titleColor = 0xFFE88080;
                title = "OUT OF STOCK";
                body = result.itemName() + " has no stock available.";
            }
            case SHOP_FULL -> {
                sideColor = 0xFFE05050; titleColor = 0xFFE88080;
                title = "SHOP IS FULL";
                body = "Shop has enough of " + result.itemName() + ".";
            }
            default -> { return; }
        }

        FlowLayout toast = Containers.verticalFlow(Sizing.fixed(320), Sizing.content());
        final int sc = sideColor;
        toast.surface(Surface.flat(0xFF111C28)
                .and(Surface.outline(0xFF2A3A4A))
                .and((ctx, comp) -> ctx.fill(comp.x(), comp.y(), comp.x() + 3, comp.y() + comp.height(), sc)));
        toast.padding(Insets.of(10, 12, 10, 16));
        toast.gap(4);
        toast.child(tint(title, titleColor));
        toast.child(tint(body, 0xFFD0DCE8));

        // Position bottom-right, aligned with footer — no overlay, no event blocking
        int toastX = this.width - 336;          // 320px wide + 16px right margin
        int toastY = this.height - 130;          // footer (~58px) + gap + toast height (~60px)
        toast.positioning(Positioning.absolute(toastX, toastY));
        toast.zIndex(200);

        this.toastWidget = toast;
        this.rootLayout.child(this.toastWidget);
        this.toastExpiryMs = System.currentTimeMillis() + 4000;
    }

    private void closeListingDialog() {
        if (this.listingDialog != null) { this.listingDialog.remove(); this.listingDialog = null; }
    }

    private UUID activeShopId() {
        UUID fromMenu = this.menu.getShopId();
        return fromMenu.equals(ZERO_UUID) ? ShopClientState.getLastShopId() : fromMenu;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.minecraft != null && this.minecraft.options.keyInventory.matches(keyCode, scanCode)) return true;
        if (keyCode == GLFW.GLFW_KEY_E) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}