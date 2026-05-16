package net.ledok.economy_ld.client.screen;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.OverlayContainer;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.core.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.economy_ld.auction.AuctionRecord;
import net.ledok.economy_ld.network.packet.c2s.BuyoutAuctionC2SPacket;
import net.ledok.economy_ld.network.packet.c2s.CancelAuctionC2SPacket;
import net.ledok.economy_ld.network.packet.c2s.PlaceAuctionC2SPacket;
import net.ledok.economy_ld.network.packet.c2s.PlaceBidC2SPacket;
import net.ledok.economy_ld.network.packet.s2c.AuctionActionResultS2CPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.stream.Collectors;

public class AuctionBrowseScreen extends BaseOwoScreen<StackLayout> {

    // ─── Colors ──────────────────────────────────────────────────────────────
    private static final int C_BG       = 0xFF0B141E;
    private static final int C_PANEL    = 0xFF0D1722;
    private static final int C_PANEL2   = 0xFF0A1018;
    private static final int C_ROW_A    = 0xFF1A2A3A;
    private static final int C_ROW_B    = 0xFF1C2D40;
    private static final int C_HAIR     = 0xFF283442;
    private static final int C_HAIR_HI  = 0xFF3A4A5C;
    private static final int C_INK      = 0xFFE8EEF5;
    private static final int C_INK_MID  = 0xFF9AA8B8;
    private static final int C_INK_DIM  = 0xFF5F6E80;
    private static final int C_AMBER    = 0xFFF5B042;
    private static final int C_AMBER_DK = 0xFFB07820;
    private static final int C_GREEN    = 0xFF5FC76C;
    private static final int C_RED      = 0xFFE05050;

    // ─── Sort / Tab ───────────────────────────────────────────────────────────
    private enum Tab { ALL, MY_LISTINGS, MY_BIDS }
    private enum SortMode {
        TIME_LEFT, PRICE_ASC, PRICE_DESC, NAME;
        SortMode next() { return values()[(ordinal() + 1) % values().length]; }
        String label() { return switch (this) {
            case TIME_LEFT  -> "SORT · TIME";
            case PRICE_ASC  -> "SORT · PRICE ↑";
            case PRICE_DESC -> "SORT · PRICE ↓";
            case NAME       -> "SORT · NAME";
        };}
    }

    // ─── State ────────────────────────────────────────────────────────────────
    private Tab activeTab   = Tab.ALL;
    private SortMode sortMode = SortMode.TIME_LEFT;
    private String searchQuery = "";
    private boolean searchOpen = false;
    private int currentPage    = 0;
    private int listingsPerPage = 6;
    private boolean compact = false;

    private int lastSyncVersion   = -1;
    private int lastActionVersion = -1;
    private int lastTab           = -1;
    private SortMode lastSort     = null;
    private String lastSearch     = null;

    // ─── Layout refs ─────────────────────────────────────────────────────────
    private StackLayout rootLayout;
    private FlowLayout contentArea;
    private FlowLayout middleSection;
    private LabelComponent pageLabel;
    private ButtonComponent prevButton, nextButton, sortButton;
    private FlowLayout pageBars;
    private FlowLayout toastWidget = null;
    private long toastExpiryMs = 0;
    private OverlayContainer<FlowLayout> activeDialog = null;
    private LabelComponent balanceLabel;
    private LabelComponent auctionCountLabel;

    // ─── Lifecycle ────────────────────────────────────────────────────────────
    public AuctionBrowseScreen() {
        super(Component.literal("Auction House"));
    }

    @Override
    protected @NotNull OwoUIAdapter<StackLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::stack);
    }

    @Override
    protected void build(StackLayout root) {
        this.rootLayout = root;
        root.surface(Surface.flat(C_BG));

        int shellW = Math.min(this.width - 32, 1020);
        int shellH = Math.min(this.height - 32, 520);
        this.compact = shellW < 720 || shellH < 360;

        int rowH = s(48) + 4;
        int reservedH = s(64) + s(28) + s(58) + s(36) + 24; // header+colH+footer+tabs
        this.listingsPerPage = Math.max(2, (shellH - reservedH) / rowH);

        FlowLayout shell = Containers.verticalFlow(Sizing.fixed(shellW), Sizing.fixed(shellH));
        shell.surface(Surface.flat(C_PANEL).and(Surface.outline(C_HAIR_HI)));

        FlowLayout panel = Containers.verticalFlow(Sizing.fill(100), Sizing.fill(100));
        panel.surface(Surface.flat(C_PANEL));
        panel.child(buildHeader());

        this.middleSection = Containers.verticalFlow(Sizing.fill(100), Sizing.expand());
        this.contentArea = Containers.verticalFlow(Sizing.fill(100), Sizing.expand());
        this.middleSection.child(buildTabBar());
        this.middleSection.child(this.contentArea);
        panel.child(this.middleSection);
        panel.child(buildFooter());
        shell.child(panel);

        FlowLayout center = Containers.verticalFlow(Sizing.fill(100), Sizing.fill(100));
        center.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        center.child(shell);
        root.child(center);

        this.lastSyncVersion   = AuctionClientState.getSyncVersion();
        this.lastActionVersion = AuctionClientState.getActionVersion();
        refreshUi(true);
    }

    @Override
    public void tick() {
        super.tick();
        // Expire toast
        if (toastWidget != null && System.currentTimeMillis() > toastExpiryMs) {
            toastWidget.remove();
            toastWidget = null;
        }
        // New action result
        int av = AuctionClientState.getActionVersion();
        if (av != lastActionVersion) {
            lastActionVersion = av;
            AuctionActionResultS2CPacket r = AuctionClientState.getLastResult();
            if (r != null) showToast(r);
        }
        refreshUi(false);
    }

    // ─── Header ───────────────────────────────────────────────────────────────
    private FlowLayout buildHeader() {
        FlowLayout header = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(s(64)));
        header.surface(Surface.flat(C_PANEL2).and(Surface.outline(C_HAIR)));
        header.padding(Insets.of(s(8)));
        header.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        header.gap(s(10));

        // Icon
        FlowLayout icon = Containers.verticalFlow(Sizing.fixed(s(34)), Sizing.fixed(s(34)));
        icon.surface(Surface.flat(C_AMBER));
        icon.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        icon.child(tint("⚖", 0xFF201210));

        // Title
        FlowLayout title = Containers.verticalFlow(Sizing.expand(), Sizing.content());
        title.gap(2);
        title.child(tint("Auction House", C_INK));
        FlowLayout sub = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        sub.gap(s(6));
        sub.child(tag("GLOBAL MARKET", C_AMBER));
        if (!compact) {
            this.auctionCountLabel = tint("0 active", C_INK_MID);
            sub.child(this.auctionCountLabel);
            this.balanceLabel = tint("BAL  ·  0 LC", 0xFF9D81EA);
            sub.child(this.balanceLabel);
        }
        title.child(sub);
        header.child(icon);
        header.child(title);

        // Controls
        FlowLayout ctrl = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        ctrl.gap(s(6));
        ctrl.child(smallBtn("⌕", s(38), s(32), 0xFF132131, 0xFF193047, C_HAIR_HI, b -> toggleSearch()));
        this.sortButton = smallBtn(sortMode.label(), compact ? 80 : 120, s(32), 0xFF132131, 0xFF193047, C_HAIR_HI, b -> {
            sortMode = sortMode.next();
            sortButton.setMessage(Component.literal(sortMode.label()));
            currentPage = 0;
            refreshUi(true);
        });
        ctrl.child(this.sortButton);
        ctrl.child(smallBtn(compact ? "+" : "+ LIST ITEM", compact ? s(32) : 110, s(32), C_AMBER_DK, C_AMBER, 0xFFF5C870, b -> openItemPicker()));
        ctrl.child(smallBtn("✕", s(32), s(32), 0xFF132131, 0xFF193047, C_HAIR_HI, b -> onClose()));
        header.child(ctrl);
        return header;
    }

    // ─── Tab Bar ──────────────────────────────────────────────────────────────
    private FlowLayout buildTabBar() {
        this.pageBars = null; // reset
        FlowLayout bar = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(s(36)));
        bar.surface(Surface.flat(C_PANEL2).and(Surface.outline(C_HAIR)));
        bar.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        bar.padding(Insets.both(0, s(8)));
        bar.gap(2);

        for (Tab t : Tab.values()) {
            String label = switch (t) { case ALL -> "ALL"; case MY_LISTINGS -> "MY LISTINGS"; case MY_BIDS -> "MY BIDS"; };
            boolean active = t == activeTab;
            int fill   = active ? C_AMBER_DK : 0xFF132131;
            int hover  = active ? C_AMBER    : 0xFF193047;
            int border = active ? C_AMBER    : C_HAIR_HI;
            int textC  = active ? 0xFF201210 : C_INK_MID;
            Tab tt = t;
            ButtonComponent btn = Components.button(Component.literal(label), b -> {
                activeTab = tt;
                currentPage = 0;
                refreshUi(true);
            });
            btn.sizing(Sizing.content(), Sizing.fixed(s(28)));
            btn.renderer((ctx, rendered, delta) -> {
                int bg = rendered.isHoveredOrFocused() ? hover : fill;
                ctx.fill(rendered.getX(), rendered.getY(), rendered.getX() + rendered.getWidth(), rendered.getY() + rendered.getHeight(), bg);
                ctx.drawRectOutline(rendered.getX(), rendered.getY(), rendered.getWidth(), rendered.getHeight(), border);
            });
            btn.setMessage(Component.literal(label)); // keep text visible
            bar.child(btn);
        }

        // Search bar in same row if open
        if (searchOpen) {
            FlowLayout sf = Containers.horizontalFlow(Sizing.expand(), Sizing.content());
            sf.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
            sf.gap(6);
            sf.padding(Insets.horizontal(8));
            sf.child(tint(">", C_INK_DIM));
            TextBoxComponent field = Components.textBox(Sizing.expand(), searchQuery);
            field.setMaxLength(64);
            field.setSuggestion(searchQuery.isEmpty() ? "name  |  @mod  |  #tag" : "");
            field.onChanged().subscribe(v -> {
                searchQuery = v;
                field.setSuggestion(v.isEmpty() ? "name  |  @mod  |  #tag" : "");
                currentPage = 0;
                refreshUi(true);
            });
            sf.child(field);
            sf.child(smallBtn("✕", 24, 24, 0xFF132131, 0xFF193047, C_HAIR_HI, b -> {
                searchOpen = false; searchQuery = ""; currentPage = 0;
                rebuildMiddle(); refreshUi(true);
            }));
            bar.child(sf);
        }
        return bar;
    }

    private void toggleSearch() {
        searchOpen = !searchOpen;
        if (!searchOpen) { searchQuery = ""; currentPage = 0; }
        rebuildMiddle();
        refreshUi(true);
    }

    private void rebuildMiddle() {
        middleSection.clearChildren();
        middleSection.child(buildTabBar());
        middleSection.child(contentArea);
    }

    // ─── Refresh ─────────────────────────────────────────────────────────────
    private void refreshUi(boolean force) {
        if (activeDialog != null && !activeDialog.hasParent()) activeDialog = null;

        List<AuctionRecord> all = AuctionClientState.getAuctions();
        List<AuctionRecord> filtered = applyFilterAndSort(all);

        int totalPages = Math.max(1, (filtered.size() + listingsPerPage - 1) / listingsPerPage);
        if (currentPage >= totalPages) currentPage = totalPages - 1;

        if (prevButton != null) prevButton.active(currentPage > 0);
        if (nextButton != null) nextButton.active(currentPage < totalPages - 1);
        if (pageLabel != null) pageLabel.text(Component.literal("PAGE  " + String.format("%02d", currentPage + 1) + " / " + String.format("%02d", totalPages)));

        // Update header labels
        if (auctionCountLabel != null)
            auctionCountLabel.text(Component.literal(all.size() + (all.size() == 1 ? " active" : " active")));
        if (balanceLabel != null)
            balanceLabel.text(Component.literal("BAL  ·  " + String.format("%,d", AuctionClientState.getPlayerBalance()) + " LC"));

        int sv = AuctionClientState.getSyncVersion();
        boolean syncNew = sv != lastSyncVersion;
        if (syncNew) lastSyncVersion = sv;

        boolean tabChanged  = activeTab.ordinal() != lastTab;
        boolean sortChanged = sortMode != lastSort;
        boolean fltChanged  = !searchQuery.equals(lastSearch != null ? lastSearch : "");
        if (force || syncNew || tabChanged || sortChanged || fltChanged) {
            rebuildContent(filtered);
            rebuildPageBars(totalPages, currentPage);
            lastTab   = activeTab.ordinal();
            lastSort  = sortMode;
            lastSearch = searchQuery;
        }
    }

    // ─── Content ─────────────────────────────────────────────────────────────
    private void rebuildContent(List<AuctionRecord> filtered) {
        contentArea.clearChildren();
        if (filtered.isEmpty()) {
            contentArea.child(buildEmpty());
        } else {
            contentArea.child(buildBody(filtered));
        }
    }

    private FlowLayout buildEmpty() {
        FlowLayout body = Containers.verticalFlow(Sizing.fill(100), Sizing.expand());
        body.surface(Surface.flat(C_ROW_A));
        body.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        body.gap(8);
        body.child(tint(activeTab == Tab.ALL ? "No active auctions." : activeTab == Tab.MY_LISTINGS ? "You have no active listings." : "You are not bidding on anything.", C_INK_MID));
        if (activeTab == Tab.ALL || activeTab == Tab.MY_LISTINGS)
            body.child(smallBtn("+ LIST AN ITEM", 160, 32, C_AMBER_DK, C_AMBER, 0xFFF5C870, b -> openItemPicker()));
        return body;
    }

    private FlowLayout buildBody(List<AuctionRecord> filtered) {
        FlowLayout body = Containers.verticalFlow(Sizing.fill(100), Sizing.expand());
        body.surface(Surface.flat(0xFF111C29).and(Surface.outline(0xFF1C2D3F)));
        body.padding(Insets.of(8));
        body.gap(4);

        // Column header
        FlowLayout colH = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        colH.surface(Surface.flat(0xFF172433));
        colH.padding(Insets.of(4));
        colH.gap(6);
        int iW = compact ? 140 : 200, sW = compact ? 0 : 70, bW = compact ? 60 : 80, tW = compact ? 55 : 70;
        colH.child(cellLabel("ITEM", iW, C_INK_DIM));
        if (!compact) colH.child(cellLabel("SELLER", sW, C_INK_DIM));
        colH.child(cellLabelR("CURRENT BID", bW, C_INK_DIM));
        colH.child(cellLabelR("TIME LEFT", tW, C_INK_DIM));
        if (!compact) colH.child(cellLabelR("BUYOUT", bW, C_INK_DIM));
        colH.child(Containers.horizontalFlow(Sizing.expand(), Sizing.content()));
        colH.child(cellLabelR("ACTIONS", 120, C_INK_DIM));
        body.child(colH);

        int start = currentPage * listingsPerPage;
        int end   = Math.min(start + listingsPerPage, filtered.size());
        UUID myUuid = minecraft != null && minecraft.player != null ? minecraft.player.getUUID() : null;
        for (int i = start; i < end; i++)
            body.child(buildRow(filtered.get(i), i, myUuid));
        return body;
    }

    private FlowLayout buildRow(AuctionRecord a, int idx, UUID myUuid) {
        int bg = idx % 2 == 0 ? C_ROW_A : C_ROW_B;
        boolean isMine  = myUuid != null && myUuid.equals(a.sellerUuid());
        boolean isMyBid = myUuid != null && myUuid.equals(a.bidderUuid());
        long remaining  = a.expiresAt() - System.currentTimeMillis() / 1000L;

        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(s(48)));
        row.surface(Surface.flat(bg));
        row.padding(Insets.of(s(4)));
        row.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        row.gap(6);

        // Item cell
        int iW = compact ? 140 : 200;
        FlowLayout itemCell = Containers.horizontalFlow(Sizing.fixed(iW), Sizing.content());
        itemCell.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        itemCell.gap(6);
        itemCell.child(Components.item(a.itemStack().copyWithCount(a.quantity())).showOverlay(true).setTooltipFromStack(true));
        FlowLayout itemTxt = Containers.verticalFlow(Sizing.expand(), Sizing.content());
        itemTxt.gap(1);
        itemTxt.child(tint(a.itemStack().getHoverName().getString() + (a.quantity() > 1 ? " ×" + a.quantity() : ""), C_INK));
        if (!compact) itemTxt.child(tint(a.itemStack().getItem().toString(), C_INK_DIM));
        itemCell.child(itemTxt);
        row.child(itemCell);

        // Seller
        if (!compact) {
            int sW = 70;
            FlowLayout sellerCell = Containers.verticalFlow(Sizing.fixed(sW), Sizing.content());
            sellerCell.gap(1);
            sellerCell.child(tint(a.sellerName(), isMine ? C_AMBER : C_INK_MID));
            if (isMine) sellerCell.child(tint("(you)", C_INK_DIM));
            row.child(sellerCell);
        }

        // Current bid
        int bW = compact ? 60 : 80;
        FlowLayout bidCell = Containers.verticalFlow(Sizing.fixed(bW), Sizing.content());
        bidCell.alignment(HorizontalAlignment.RIGHT, VerticalAlignment.CENTER);
        bidCell.gap(1);
        boolean hasBids = a.bidderUuid() != null;
        int bidColor = isMyBid ? C_AMBER : (hasBids ? C_GREEN : C_INK_MID);
        bidCell.child(cellLabelR(String.format("%,d", a.currentBid()) + " LC", bW, bidColor));
        if (isMyBid && !compact) bidCell.child(cellLabelR("(your bid)", bW, C_INK_DIM));
        row.child(bidCell);

        // Time left
        int tW = compact ? 55 : 70;
        int timeColor = remaining > 86400 ? C_INK_MID : (remaining > 3600 ? C_AMBER : C_RED);
        row.child(cellLabelR(formatTime(remaining), tW, timeColor));

        // Buyout
        if (!compact) {
            row.child(cellLabelR(a.buyoutPrice() != null ? String.format("%,d", a.buyoutPrice()) + " LC" : "–", bW, a.buyoutPrice() != null ? 0xFFFFD080 : C_INK_DIM));
        }

        row.child(Containers.horizontalFlow(Sizing.expand(), Sizing.content()));

        // Actions
        FlowLayout actions = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        actions.alignment(HorizontalAlignment.RIGHT, VerticalAlignment.CENTER);
        actions.gap(4);

        if (isMine) {
            boolean canCancel = !hasBids;
            ButtonComponent cancel = Components.button(Component.literal("CANCEL"), b -> {
                if (canCancel) ClientPlayNetworking.send(new CancelAuctionC2SPacket(a.id()));
                else openCancelBlockedDialog();
            });
            cancel.sizing(Sizing.fixed(s(70)), Sizing.fixed(s(22)));
            cancel.renderer((ctx, rendered, delta) -> {
                int col = canCancel ? (rendered.isHoveredOrFocused() ? 0xFFB83A2A : 0xFF9A2A1C) : 0xFF3C3030;
                ctx.fill(rendered.getX(), rendered.getY(), rendered.getX() + rendered.getWidth(), rendered.getY() + rendered.getHeight(), col);
                ctx.drawRectOutline(rendered.getX(), rendered.getY(), rendered.getWidth(), rendered.getHeight(), canCancel ? 0xFFD04040 : C_HAIR);
            });
            actions.child(cancel);
        } else {
            if (a.buyoutPrice() != null) {
                actions.child(smallBtn("BUY NOW", s(62), s(22), 0xFF2A1E08, 0xFF3D2E10, 0xFFF5B042, b -> openBuyoutConfirm(a)));
            }
            boolean canBid = !isMine && remaining > 0;
            if (canBid) {
                actions.child(smallBtn("BID", s(48), s(22), 0xFF0D1E30, 0xFF142A42, 0xFF4A90D8, b -> openBidDialog(a)));
            }
        }

        row.child(actions);
        return row;
    }

    // ─── Footer ───────────────────────────────────────────────────────────────
    private FlowLayout buildFooter() {
        FlowLayout footer = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(s(58)));
        footer.surface(Surface.flat(C_PANEL2).and(Surface.outline(C_HAIR)));
        footer.padding(Insets.of(s(12)));
        footer.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        this.prevButton = smallBtn("◀ PREV", s(78), s(30), 0xFF1A2736, 0xFF223347, 0xFF3A4F67);
        prevButton.onPress(b -> { if (currentPage > 0) { currentPage--; refreshUi(true); } });

        this.nextButton = smallBtn("NEXT ▶", s(78), s(30), 0xFF1A2736, 0xFF223347, 0xFF3A4F67);
        nextButton.onPress(b -> { currentPage++; refreshUi(true); });

        FlowLayout center = Containers.horizontalFlow(Sizing.expand(), Sizing.content());
        center.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        center.gap(10);

        this.pageBars = Containers.horizontalFlow(Sizing.fixed(150), Sizing.fixed(4));
        this.pageBars.gap(2);
        this.pageLabel = tint("PAGE  01 / 01", C_INK_MID);
        center.child(pageBars);
        center.child(pageLabel);

        footer.child(prevButton);
        footer.child(center);
        footer.child(nextButton);
        return footer;
    }

    private void rebuildPageBars(int total, int cur) {
        if (pageBars == null) return;
        pageBars.clearChildren();
        int containerW = 150, gap = 2;
        int usable = Math.max(total, containerW - gap * Math.max(0, total - 1));
        int base = usable / total, rem = usable % total;
        for (int i = 0; i < total; i++) {
            var bar = Components.box(Sizing.fixed(base + (i < rem ? 1 : 0)), Sizing.fill(100));
            bar.fill(true);
            bar.color(i == cur ? Color.ofRgb(0xF5B042) : Color.ofRgb(0x3B4D61));
            pageBars.child(bar);
        }
    }

    // ─── Filter / Sort ───────────────────────────────────────────────────────
    private List<AuctionRecord> applyFilterAndSort(List<AuctionRecord> all) {
        UUID myUuid = minecraft != null && minecraft.player != null ? minecraft.player.getUUID() : null;
        var stream = all.stream();

        // Tab filter
        if (myUuid != null) {
            UUID u = myUuid;
            stream = switch (activeTab) {
                case MY_LISTINGS -> stream.filter(a -> u.equals(a.sellerUuid()));
                case MY_BIDS     -> stream.filter(a -> u.equals(a.bidderUuid()));
                default -> stream;
            };
        }

        // Search tokens
        if (!searchQuery.isBlank()) {
            String[] tokens = searchQuery.trim().toLowerCase(Locale.ROOT).split("\\s+");
            stream = stream.filter(a -> {
                for (String token : tokens) {
                    if (token.startsWith("@")) {
                        String ns = token.substring(1);
                        String itemId = a.itemStack().getItem().toString().toLowerCase(Locale.ROOT);
                        String namespace = itemId.contains(":") ? itemId.split(":")[0] : itemId;
                        if (!namespace.contains(ns)) return false;
                    } else if (token.startsWith("#")) {
                        String tq = token.substring(1);
                        boolean match = a.itemStack().getTags().anyMatch(tag -> {
                            String path = tag.location().getPath().toLowerCase(Locale.ROOT);
                            String full = tag.location().toString().toLowerCase(Locale.ROOT);
                            return path.contains(tq) || full.contains(tq);
                        });
                        if (!match) return false;
                    } else {
                        String name = a.itemStack().getHoverName().getString().toLowerCase(Locale.ROOT);
                        String id   = a.itemStack().getItem().toString().toLowerCase(Locale.ROOT);
                        if (!name.contains(token) && !id.contains(token)) return false;
                    }
                }
                return true;
            });
        }

        // Sort
        long now = System.currentTimeMillis() / 1000L;
        stream = switch (sortMode) {
            case TIME_LEFT  -> stream.sorted(Comparator.comparingLong(a -> a.expiresAt() - now));
            case PRICE_ASC  -> stream.sorted(Comparator.comparingLong(AuctionRecord::currentBid));
            case PRICE_DESC -> stream.sorted(Comparator.comparingLong(AuctionRecord::currentBid).reversed());
            case NAME       -> stream.sorted(Comparator.comparing(a -> a.itemStack().getHoverName().getString()));
        };

        return stream.collect(Collectors.toList());
    }

    // ─── Bid Dialog ──────────────────────────────────────────────────────────
    private void openBidDialog(AuctionRecord a) {
        if (activeDialog != null) return;
        long minBid = a.currentBid() + 1;
        final String[] bidVal = {String.valueOf(minBid)};

        FlowLayout panel = Containers.verticalFlow(Sizing.fixed(420), Sizing.content());
        panel.surface(Surface.flat(0xFF111C28).and(Surface.outline(C_HAIR_HI)));
        panel.padding(Insets.of(14));
        panel.gap(10);

        // Header
        FlowLayout head = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        head.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        FlowLayout htxt = Containers.verticalFlow(Sizing.expand(), Sizing.content());
        htxt.gap(2);
        htxt.child(tint("Place a Bid", C_INK));
        htxt.child(tint(a.itemStack().getItem().toString().toUpperCase(), C_INK_DIM));
        head.child(htxt);
        head.child(smallBtn("✕", 28, 28, 0xFF132131, 0xFF193047, C_HAIR_HI, b -> closeDialog()));
        panel.child(head);

        // Item card
        FlowLayout card = buildItemCard(a);
        panel.child(card);

        // Bid info
        FlowLayout info = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        info.surface(Surface.flat(C_ROW_A).and(Surface.outline(C_HAIR)));
        info.padding(Insets.of(10));
        info.gap(4);
        info.child(tint("CURRENT BID  ·  " + String.format("%,d", a.currentBid()) + " LC", C_INK_MID));
        info.child(tint("MIN NEXT BID  ·  " + String.format("%,d", minBid) + " LC", C_AMBER));
        panel.child(info);

        // Bid field
        FlowLayout fieldRow = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        fieldRow.gap(6);
        FlowLayout fh = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        fh.child(tint("YOUR BID", C_INK_MID));
        fh.child(Containers.horizontalFlow(Sizing.expand(), Sizing.content()));
        fh.child(tint("must be > current bid", C_INK_DIM));
        fieldRow.child(fh);

        FlowLayout inputWrap = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(40));
        inputWrap.surface(Surface.flat(0xFF111C28).and(Surface.outline(0xFF4A7AAA)));
        inputWrap.padding(Insets.of(6));
        inputWrap.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        inputWrap.gap(6);
        TextBoxComponent input = Components.textBox(Sizing.expand(), String.valueOf(minBid));
        input.setMaxLength(16);
        input.onChanged().subscribe(v -> bidVal[0] = v);
        inputWrap.child(input);
        inputWrap.child(tint("LC", C_INK_DIM));
        fieldRow.child(inputWrap);
        panel.child(fieldRow);

        // Error
        LabelComponent err = tint("", C_RED);
        panel.child(err);

        // Actions
        FlowLayout actions = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        actions.alignment(HorizontalAlignment.RIGHT, VerticalAlignment.CENTER);
        actions.gap(6);
        actions.child(smallBtn("CANCEL", 90, 32, 0xFF1B2A3B, 0xFF22354A, C_HAIR_HI, b -> closeDialog()));
        actions.child(smallBtn("CONFIRM BID", 112, 32, 0xFF0D2540, 0xFF142E4E, 0xFF4A90D8, b -> {
            long bid = parseLong(bidVal[0], 0);
            if (bid <= a.currentBid()) {
                err.text(Component.literal("Bid must be greater than " + String.format("%,d", a.currentBid()) + " LC."));
                return;
            }
            ClientPlayNetworking.send(new PlaceBidC2SPacket(a.id(), bid));
            closeDialog();
        }));
        panel.child(actions);

        showDialog(panel);
    }

    // ─── Buyout Confirm ───────────────────────────────────────────────────────
    private void openBuyoutConfirm(AuctionRecord a) {
        if (activeDialog != null) return;
        if (a.buyoutPrice() == null) return;

        FlowLayout panel = Containers.verticalFlow(Sizing.fixed(400), Sizing.content());
        panel.surface(Surface.flat(0xFF111C28).and(Surface.outline(0xFF4A3010)));
        panel.padding(Insets.of(14));
        panel.gap(10);

        FlowLayout head = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        head.surface(Surface.flat(0xFF1A1A08).and((ctx, comp) -> ctx.fill(comp.x(), comp.y(), comp.x() + 3, comp.y() + comp.height(), C_AMBER)));
        head.padding(Insets.of(10, 12, 8, 16));
        head.gap(3);
        head.child(tint("Buy Now", C_AMBER));
        head.child(tint("INSTANTLY WIN THIS AUCTION", C_INK_DIM));
        panel.child(head);

        panel.child(buildItemCard(a));

        FlowLayout info = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        info.surface(Surface.flat(C_ROW_A).and(Surface.outline(C_HAIR)));
        info.padding(Insets.of(10));
        info.gap(4);
        info.child(tint("BUYOUT PRICE  ·  " + String.format("%,d", a.buyoutPrice()) + " LC", C_AMBER));
        info.child(tint("This will end the auction immediately.", C_INK_MID));
        panel.child(info);

        FlowLayout actions = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        actions.alignment(HorizontalAlignment.RIGHT, VerticalAlignment.CENTER);
        actions.gap(6);
        actions.child(smallBtn("CANCEL", 90, 32, 0xFF1B2A3B, 0xFF22354A, C_HAIR_HI, b -> closeDialog()));
        long price = a.buyoutPrice();
        actions.child(smallBtn("BUY NOW  " + String.format("%,d", price) + " LC", 180, 32, C_AMBER_DK, C_AMBER, 0xFFF5C870, b -> {
            ClientPlayNetworking.send(new BuyoutAuctionC2SPacket(a.id()));
            closeDialog();
        }));
        panel.child(actions);

        showDialog(panel);
    }

    // ─── Cancel blocked ───────────────────────────────────────────────────────
    private void openCancelBlockedDialog() {
        if (activeDialog != null) return;
        FlowLayout panel = Containers.verticalFlow(Sizing.fixed(360), Sizing.content());
        panel.surface(Surface.flat(0xFF111C28).and(Surface.outline(C_HAIR_HI)));
        panel.padding(Insets.of(14));
        panel.gap(10);
        panel.child(tint("Cannot Cancel", C_RED));
        panel.child(tint("This auction already has bids and cannot be cancelled.", C_INK_MID));
        FlowLayout actions = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        actions.alignment(HorizontalAlignment.RIGHT, VerticalAlignment.CENTER);
        actions.child(smallBtn("OK", 70, 32, C_AMBER_DK, C_AMBER, 0xFFF5C870, b -> closeDialog()));
        panel.child(actions);
        showDialog(panel);
    }

    // ─── Item Picker → List Dialog ────────────────────────────────────────────
    private void openItemPicker() {
        if (activeDialog != null) return;
        if (minecraft == null || minecraft.player == null) return;

        Map<String, ItemStack> merged = new LinkedHashMap<>();
        var inv = minecraft.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            String key = s.getItem().toString() + s.getComponentsPatch();
            if (merged.containsKey(key)) {
                ItemStack existing = merged.get(key);
                existing.setCount(existing.getCount() + s.getCount());
            } else {
                merged.put(key, s.copy());
            }
        }
        List<ItemStack> slots = new ArrayList<>(merged.values());

        FlowLayout panel = Containers.verticalFlow(Sizing.fixed(480), Sizing.content());
        panel.surface(Surface.flat(0xFF111C28).and(Surface.outline(C_HAIR_HI)));
        panel.padding(Insets.of(14));
        panel.gap(10);

        FlowLayout head = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        head.gap(3);
        FlowLayout headRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        headRow.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        FlowLayout ht = Containers.verticalFlow(Sizing.expand(), Sizing.content());
        ht.gap(2);
        ht.child(tint("List an Item", C_INK));
        ht.child(tint("SELECT AN ITEM FROM YOUR INVENTORY", C_INK_DIM));
        headRow.child(ht);
        headRow.child(smallBtn("✕", 28, 28, 0xFF132131, 0xFF193047, C_HAIR_HI, b -> closeDialog()));
        panel.child(headRow);

        FlowLayout body = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        body.surface(Surface.flat(C_ROW_A).and(Surface.outline(C_HAIR)));
        body.padding(Insets.of(12));
        body.gap(4);

        if (slots.isEmpty()) {
            FlowLayout empty = Containers.verticalFlow(Sizing.fill(100), Sizing.fixed(60));
            empty.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
            empty.child(tint("Your inventory is empty.", C_INK_DIM));
            body.child(empty);
        } else {
            final int COLS = 9;
            for (int row = 0; row < Math.ceil(slots.size() / (double) COLS); row++) {
                FlowLayout rowLayout = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
                rowLayout.gap(4);
                for (int col = 0; col < COLS; col++) {
                    int idx = row * COLS + col;
                    if (idx >= slots.size()) {
                        FlowLayout empty = Containers.verticalFlow(Sizing.fixed(40), Sizing.fixed(40));
                        empty.surface(Surface.flat(0xFF131E2B).and(Surface.outline(0xFF1E2F40)));
                        rowLayout.child(empty);
                    } else {
                        ItemStack stack = slots.get(idx);
                        FlowLayout slot = Containers.verticalFlow(Sizing.fixed(40), Sizing.fixed(40));
                        slot.surface(Surface.flat(0xFF1E2E3E).and(Surface.outline(0xFF334A60)));
                        slot.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
                        slot.child(Components.item(stack).showOverlay(true).setTooltipFromStack(true));
                        ButtonComponent slotBtn = Components.button(Component.empty(), b -> {
                            closeDialog();
                            openListDialog(stack.copyWithCount(1));
                        });
                        slotBtn.sizing(Sizing.fixed(40), Sizing.fixed(40));
                        slotBtn.renderer((ctx, rendered, delta) -> {
                            if (rendered.isHoveredOrFocused()) {
                                ctx.fill(rendered.getX(), rendered.getY(), rendered.getX() + rendered.getWidth(), rendered.getY() + rendered.getHeight(), 0x44F5B042);
                                ctx.drawRectOutline(rendered.getX(), rendered.getY(), rendered.getWidth(), rendered.getHeight(), C_AMBER);
                            }
                        });
                        StackLayout ss = Containers.stack(Sizing.fixed(40), Sizing.fixed(40));
                        ss.child(slot);
                        ss.child(slotBtn);
                        rowLayout.child(ss);
                    }
                }
                body.child(rowLayout);
            }
        }
        panel.child(body);

        FlowLayout actions = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        actions.alignment(HorizontalAlignment.RIGHT, VerticalAlignment.CENTER);
        actions.child(smallBtn("CANCEL", 90, 32, 0xFF1B2A3B, 0xFF22354A, C_HAIR_HI, b -> closeDialog()));
        panel.child(actions);

        showDialog(panel);
    }

    private void openListDialog(ItemStack selectedStack) {
        if (activeDialog != null) return;
        if (minecraft == null || minecraft.level == null) return;

        final String[] startPriceVal = {"100"};
        final String[] buyoutVal     = {"0"};
        final String[] qtyVal        = {String.valueOf(Math.min(selectedStack.getMaxStackSize(), 1))};
        final long[]   durationSec   = {3600L}; // default 1h

        FlowLayout panel = Containers.verticalFlow(Sizing.fixed(500), Sizing.content());
        panel.surface(Surface.flat(0xFF111C28).and(Surface.outline(C_HAIR_HI)));
        panel.padding(Insets.of(14));
        panel.gap(10);

        // Header
        FlowLayout headRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        headRow.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        FlowLayout ht = Containers.verticalFlow(Sizing.expand(), Sizing.content());
        ht.gap(2);
        ht.child(tint("New Listing", C_INK));
        ht.child(tint(selectedStack.getItem().toString().toUpperCase(), C_INK_DIM));
        headRow.child(ht);
        headRow.child(smallBtn("◀ BACK", 70, 28, 0xFF132131, 0xFF193047, C_HAIR_HI, b -> { closeDialog(); openItemPicker(); }));
        headRow.child(smallBtn("✕", 28, 28, 0xFF132131, 0xFF193047, C_HAIR_HI, b -> closeDialog()));
        panel.child(headRow);

        // Item card with qty
        FlowLayout card = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        card.surface(Surface.flat(C_ROW_A).and(Surface.outline(C_HAIR)));
        card.padding(Insets.of(12));
        card.gap(10);
        card.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        card.child(Components.item(selectedStack).showOverlay(true).setTooltipFromStack(true));
        FlowLayout ct = Containers.verticalFlow(Sizing.expand(), Sizing.content());
        ct.gap(2);
        ct.child(tint(selectedStack.getHoverName().getString(), C_INK));
        ct.child(tint("Will be taken from inventory on confirm", C_INK_DIM));
        card.child(ct);
        panel.child(card);

        // Fields row 1: start price + buyout
        // Fee label — defined before fields so updateFee can be passed as callback
        LabelComponent feeLabel = tint("Set a start price to see the listing fee.", C_INK_DIM);
        Runnable updateFee = () -> {
            long price = parseLong(startPriceVal[0], 0);
            if (price > 0) {
                int feePercent = AuctionClientState.getListingFeePercent();
                long feeAmount = (price * feePercent) / 100L;
                feeLabel.text(Component.literal(
                        "Listing fee: " + String.format("%,d", feeAmount) + " LC (" + feePercent + "%)" +
                                " — deducted from your wallet at listing time."
                ));
            } else {
                feeLabel.text(Component.literal("Set a start price to see the listing fee."));
            }
        };

        FlowLayout row1 = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row1.gap(12);
        row1.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        buildField(row1, "START PRICE", "opening bid", "100", "LC", startPriceVal, true, updateFee);
        buildField(row1, "BUYOUT PRICE", "0 = no buyout", "0", "LC", buyoutVal, false);
        panel.child(row1);

        // Qty field (only if stackable)
        if (selectedStack.getMaxStackSize() > 1) {
            FlowLayout row2 = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
            row2.gap(12);
            buildField(row2, "QUANTITY", "how many to sell", "1", "×", qtyVal, false);
            panel.child(row2);
        }

        // Duration picker
        FlowLayout durationSection = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        durationSection.gap(6);
        FlowLayout durationHead = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        durationHead.child(tint("DURATION", C_INK_MID));
        durationHead.child(Containers.horizontalFlow(Sizing.expand(), Sizing.content()));
        LabelComponent selectedDurLabel = tint("1 HOUR", C_AMBER);
        durationHead.child(selectedDurLabel);
        durationSection.child(durationHead);
        FlowLayout dBtns = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        dBtns.gap(6);
        record Dur(String label, long secs) {}
        Dur[] durations = {new Dur("1 HOUR", 3600), new Dur("6 HOURS", 21600), new Dur("24 HOURS", 86400), new Dur("48 HOURS", 172800)};
        List<ButtonComponent> durBtnList = new ArrayList<>();
        for (Dur d : durations) {
            long ds = d.secs();
            String dl = d.label();
            boolean isDefault = ds == 3600;
            ButtonComponent durBtn = Components.button(Component.literal(dl), b -> {
                durationSec[0] = ds;
                selectedDurLabel.text(Component.literal(dl));
                // Update all button appearances
                for (ButtonComponent ob : durBtnList) {
                    boolean sel = ob == b;
                    ob.renderer((ctx, rendered, delta) -> {
                        int fill  = sel ? C_AMBER_DK : 0xFF1B2A3B;
                        int hover = sel ? C_AMBER    : 0xFF22354A;
                        int bord  = sel ? C_AMBER    : C_HAIR_HI;
                        int col   = rendered.isHoveredOrFocused() ? hover : fill;
                        ctx.fill(rendered.getX(), rendered.getY(), rendered.getX() + rendered.getWidth(), rendered.getY() + rendered.getHeight(), col);
                        ctx.drawRectOutline(rendered.getX(), rendered.getY(), rendered.getWidth(), rendered.getHeight(), bord);
                    });
                }
            });
            durBtn.sizing(Sizing.fixed(90), Sizing.fixed(30));
            final boolean sel = isDefault;
            durBtn.renderer((ctx, rendered, delta) -> {
                int fill  = sel ? C_AMBER_DK : 0xFF1B2A3B;
                int hover = sel ? C_AMBER    : 0xFF22354A;
                int bord  = sel ? C_AMBER    : C_HAIR_HI;
                int col   = rendered.isHoveredOrFocused() ? hover : fill;
                ctx.fill(rendered.getX(), rendered.getY(), rendered.getX() + rendered.getWidth(), rendered.getY() + rendered.getHeight(), col);
                ctx.drawRectOutline(rendered.getX(), rendered.getY(), rendered.getWidth(), rendered.getHeight(), bord);
            });
            durBtnList.add(durBtn);
            dBtns.child(durBtn);
        }
        durationSection.child(dBtns);
        panel.child(durationSection);
        panel.child(feeLabel);

        // Error label
        LabelComponent errLabel = tint("", C_RED);
        panel.child(errLabel);

        // Actions
        FlowLayout actions = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        actions.alignment(HorizontalAlignment.RIGHT, VerticalAlignment.CENTER);
        actions.gap(6);
        actions.child(smallBtn("CANCEL", 90, 32, 0xFF1B2A3B, 0xFF22354A, C_HAIR_HI, b -> closeDialog()));
        actions.child(smallBtn("LIST ITEM", 100, 32, C_AMBER_DK, C_AMBER, 0xFFF5C870, b -> {
            long startPrice = parseLong(startPriceVal[0], 0);
            if (startPrice <= 0) { errLabel.text(Component.literal("Starting price must be greater than 0.")); return; }
            long buyout = parseLong(buyoutVal[0], 0);
            if (buyout > 0 && buyout <= startPrice) { errLabel.text(Component.literal("Buyout must be greater than start price.")); return; }
            int qty = (int) Math.max(1, parseLong(qtyVal[0], 1));
            if (minecraft.level == null) return;
            if (!(selectedStack.saveOptional(minecraft.level.registryAccess()) instanceof CompoundTag nbt)) { errLabel.text(Component.literal("Failed to serialize item.")); return; }
            ClientPlayNetworking.send(new PlaceAuctionC2SPacket(nbt, qty, startPrice, buyout > 0 ? buyout : null, durationSec[0]));
            closeDialog();
        }));
        panel.child(actions);

        showDialog(panel);
    }

    // ─── Toast ────────────────────────────────────────────────────────────────
    private void showToast(AuctionActionResultS2CPacket r) {
        if (toastWidget != null) { toastWidget.remove(); toastWidget = null; }

        int side, titleC;
        String title, body;

        switch (r.actionType()) {
            case LISTED -> { side = C_GREEN; titleC = C_GREEN; title = "LISTED"; body = r.itemName() + " listed successfully."; }
            case BID_PLACED -> { side = 0xFF4A90D8; titleC = 0xFF7AB8F0; title = "BID PLACED"; body = "Bid of " + String.format("%,d", r.lcAmount()) + " LC on " + r.itemName(); }
            case OUTBID -> { side = C_AMBER; titleC = C_AMBER; title = "OUTBID"; body = "You were outbid on " + r.itemName() + "."; }
            case BUYOUT -> { side = C_AMBER; titleC = C_AMBER; title = "BOUGHT"; body = "Won " + r.itemName() + " for " + String.format("%,d", r.lcAmount()) + " LC"; }
            case ITEM_SENT_TO_INBOX -> { side = C_AMBER; titleC = C_AMBER; title = "ITEM SENT TO INBOX"; body = "Open /ah inbox to collect your item."; }
            case CANCELLED -> { side = C_INK_MID; titleC = C_INK_MID; title = "CANCELLED"; body = r.itemName() + " listing cancelled."; }
            case INSUFFICIENT_FUNDS -> { side = C_RED; titleC = 0xFFE88080; title = "INSUFFICIENT FUNDS"; body = "Need " + String.format("%,d", r.lcAmount()) + " LC."; }
            case NOT_ENOUGH_ITEMS -> { side = C_RED; titleC = 0xFFE88080; title = "NOT ENOUGH ITEMS"; body = "You don't have enough " + r.itemName() + " in your inventory."; }
            case LIMIT_REACHED -> { side = C_RED; titleC = 0xFFE88080; title = "LIMIT REACHED"; body = "You have reached your listing limit."; }
            case ALREADY_ENDED -> { side = C_RED; titleC = 0xFFE88080; title = "ALREADY ENDED"; body = r.itemName() + " auction has ended."; }
            default -> { return; }
        }

        FlowLayout toast = Containers.verticalFlow(Sizing.fixed(320), Sizing.content());
        final int sc = side;
        toast.surface(Surface.flat(0xFF111C28).and(Surface.outline(C_HAIR)).and((ctx, comp) -> ctx.fill(comp.x(), comp.y(), comp.x() + 3, comp.y() + comp.height(), sc)));
        toast.padding(Insets.of(10, 12, 10, 16));
        toast.gap(4);
        toast.child(tint(title, titleC));
        toast.child(tint(body, C_INK_MID));

        int toastX = this.width - 336;
        int toastY = this.height - 130;
        toast.positioning(Positioning.absolute(toastX, toastY));
        toast.zIndex(200);

        toastWidget = toast;
        rootLayout.child(toastWidget);
        toastExpiryMs = System.currentTimeMillis() + 4000;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────
    private FlowLayout buildItemCard(AuctionRecord a) {
        FlowLayout card = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        card.surface(Surface.flat(C_ROW_A).and(Surface.outline(C_HAIR)));
        card.padding(Insets.of(12));
        card.gap(10);
        card.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        card.child(Components.item(a.itemStack().copyWithCount(a.quantity())).showOverlay(true).setTooltipFromStack(true));
        FlowLayout ct = Containers.verticalFlow(Sizing.expand(), Sizing.content());
        ct.gap(2);
        ct.child(tint(a.itemStack().getHoverName().getString() + (a.quantity() > 1 ? " ×" + a.quantity() : ""), C_INK));
        ct.child(tint("Seller: " + a.sellerName(), C_INK_DIM));
        card.child(ct);
        return card;
    }

    private void buildField(FlowLayout parent, String label, String hint, String initial, String suffix, String[] ref, boolean required) {
        buildField(parent, label, hint, initial, suffix, ref, required, null);
    }

    private void buildField(FlowLayout parent, String label, String hint, String initial, String suffix, String[] ref, boolean required, Runnable onChange) {
        FlowLayout field = Containers.verticalFlow(Sizing.fixed(220), Sizing.content());
        field.gap(4);
        FlowLayout fh = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        fh.child(tint(label, C_INK_MID));
        fh.child(Containers.horizontalFlow(Sizing.expand(), Sizing.content()));
        fh.child(tint(hint, C_INK_DIM));
        field.child(fh);
        FlowLayout wrap = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(38));
        int borderColor = required ? C_AMBER : C_HAIR_HI;
        wrap.surface(Surface.flat(0xFF111C28).and(Surface.outline(borderColor)));
        wrap.padding(Insets.of(6));
        wrap.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        wrap.gap(6);
        TextBoxComponent input = Components.textBox(Sizing.expand(), initial);
        input.setMaxLength(16);
        input.onChanged().subscribe(v -> { ref[0] = v; if (onChange != null) onChange.run(); });
        wrap.child(input);
        wrap.child(tint(suffix, C_INK_DIM));
        field.child(wrap);
        parent.child(field);
    }

    private void showDialog(FlowLayout panel) {
        activeDialog = Containers.overlay(panel);
        activeDialog.closeOnClick(false);
        activeDialog.surface(Surface.flat(0x88000000));
        activeDialog.zIndex(300);
        rootLayout.child(activeDialog);
    }

    private void closeDialog() {
        if (activeDialog != null) { activeDialog.remove(); activeDialog = null; }
    }

    private static String formatTime(long sec) {
        if (sec <= 0) return "ENDED";
        if (sec < 60) return sec + "s";
        if (sec < 3600) return (sec / 60) + "m";
        if (sec < 86400) return (sec / 3600) + "h " + ((sec % 3600) / 60) + "m";
        return (sec / 86400) + "d " + ((sec % 86400) / 3600) + "h";
    }

    private static long parseLong(String s, long fallback) {
        if (s == null || s.isBlank()) return fallback;
        try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return fallback; }
    }

    private int s(int v) { return compact ? Math.max(v * 2 / 3, 12) : v; }

    private FlowLayout tag(String text, int color) {
        FlowLayout t = Containers.verticalFlow(Sizing.content(), Sizing.content());
        t.surface(Surface.flat(color + 0x22000000).and(Surface.outline(color)));
        t.padding(Insets.both(6, 1));
        t.child(tint(text, color));
        return t;
    }

    private LabelComponent tint(String text, int color) {
        LabelComponent l = Components.label(Component.literal(text));
        l.color(Color.ofArgb(color));
        return l;
    }

    private LabelComponent cellLabel(String text, int width, int color) {
        LabelComponent l = tint(text, color);
        l.horizontalSizing(Sizing.fixed(width));
        l.maxWidth(width);
        return l;
    }

    private LabelComponent cellLabelR(String text, int width, int color) {
        LabelComponent l = cellLabel(text, width, color);
        l.horizontalTextAlignment(HorizontalAlignment.RIGHT);
        return l;
    }

    private ButtonComponent smallBtn(String text, int w, int h, int fill, int hover, int border) {
        return smallBtn(text, w, h, fill, hover, border, b -> {});
    }

    private ButtonComponent smallBtn(String text, int w, int h, int fill, int hover, int border, java.util.function.Consumer<ButtonComponent> onPress) {
        ButtonComponent btn = Components.button(Component.literal(text), onPress);
        btn.sizing(Sizing.fixed(w), Sizing.fixed(h));
        btn.renderer((ctx, rendered, delta) -> {
            int col = rendered.isHoveredOrFocused() ? hover : fill;
            ctx.fill(rendered.getX(), rendered.getY(), rendered.getX() + rendered.getWidth(), rendered.getY() + rendered.getHeight(), col);
            ctx.drawRectOutline(rendered.getX(), rendered.getY(), rendered.getWidth(), rendered.getHeight(), border);
        });
        return btn;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_E || keyCode == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}