package net.ledok.economy_ld.client.screen;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.core.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.economy_ld.auction.PendingDelivery;
import net.ledok.economy_ld.network.packet.c2s.ClaimAllInboxC2SPacket;
import net.ledok.economy_ld.network.packet.c2s.ClaimInboxItemC2SPacket;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class AuctionInboxScreen extends BaseOwoScreen<StackLayout> {

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

    // ─── State ────────────────────────────────────────────────────────────────
    private int lastSyncVersion = -1;
    private boolean compact = false;
    private int currentPage = 0;
    private int rowsPerPage = 5;
    private LabelComponent pageLabel;
    private ButtonComponent prevButton, nextButton;
    private FlowLayout pageBars;

    // ─── Layout refs ─────────────────────────────────────────────────────────
    private StackLayout rootLayout;
    private FlowLayout contentArea;
    private LabelComponent countLabel;

    public AuctionInboxScreen() {
        super(Component.literal("Auction Inbox"));
    }

    @Override
    protected @NotNull OwoUIAdapter<StackLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::stack);
    }

    @Override
    protected void build(StackLayout root) {
        this.rootLayout = root;
        root.surface(Surface.flat(C_BG));

        int shellW = Math.min(this.width - 32, 780);
        int shellH = Math.min(this.height - 32, 500);
        this.compact = shellW < 600 || shellH < 340;
        int rowH = s(52) + 4;
        int reservedH = s(64) + s(28) + s(52) + 24; // header + colHeader + footer + padding
        this.rowsPerPage = Math.max(2, (shellH - reservedH) / rowH);

        FlowLayout shell = Containers.verticalFlow(Sizing.fixed(shellW), Sizing.fixed(shellH));
        shell.surface(Surface.flat(C_PANEL).and(Surface.outline(C_HAIR_HI)));

        FlowLayout panel = Containers.verticalFlow(Sizing.fill(100), Sizing.fill(100));
        panel.child(buildHeader());

        this.contentArea = Containers.verticalFlow(Sizing.fill(100), Sizing.expand());
        panel.child(contentArea);
        panel.child(buildFooter());
        shell.child(panel);

        FlowLayout center = Containers.verticalFlow(Sizing.fill(100), Sizing.fill(100));
        center.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        center.child(shell);
        root.child(center);

        this.lastSyncVersion = InboxClientState.getSyncVersion();
        refreshUi();
    }

    @Override
    public void tick() {
        super.tick();
        int sv = InboxClientState.getSyncVersion();
        if (sv != lastSyncVersion) {
            lastSyncVersion = sv;
            refreshUi();
        }
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
        icon.surface(Surface.flat(C_AMBER_DK));
        icon.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        icon.child(tint("✉", 0xFF201210));

        // Title
        FlowLayout title = Containers.verticalFlow(Sizing.expand(), Sizing.content());
        title.gap(2);
        title.child(tint("Auction Inbox", C_INK));
        FlowLayout sub = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        sub.gap(s(6));
        sub.child(tag("UNCLAIMED ITEMS", C_AMBER));
        this.countLabel = tint("0 items", C_INK_MID);
        sub.child(countLabel);
        title.child(sub);

        // Controls
        FlowLayout ctrl = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        ctrl.gap(s(6));
        ctrl.child(smallBtn("✕", s(32), s(32), 0xFF132131, 0xFF193047, C_HAIR_HI, b -> onClose()));
        header.child(icon);
        header.child(title);
        header.child(ctrl);
        return header;
    }

    // ─── Footer ───────────────────────────────────────────────────────────────
    private FlowLayout buildFooter() {
        FlowLayout footer = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(s(52)));
        footer.surface(Surface.flat(C_PANEL2).and(Surface.outline(C_HAIR)));
        footer.padding(Insets.of(s(8)));
        footer.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        footer.gap(8);

        this.prevButton = smallBtn("◀", s(36), s(32), 0xFF1A2736, 0xFF223347, 0xFF3A4F67,
                b -> { if (currentPage > 0) { currentPage--; refreshUi(); } });
        footer.child(prevButton);

        FlowLayout center = Containers.horizontalFlow(Sizing.expand(), Sizing.content());
        center.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        center.gap(8);
        this.pageBars = Containers.horizontalFlow(Sizing.fixed(100), Sizing.fixed(4));
        this.pageBars.gap(2);
        this.pageLabel = tint("PAGE  01 / 01", C_INK_MID);
        center.child(pageBars);
        center.child(pageLabel);
        footer.child(center);

        this.nextButton = smallBtn("▶", s(36), s(32), 0xFF1A2736, 0xFF223347, 0xFF3A4F67,
                b -> { currentPage++; refreshUi(); });
        footer.child(nextButton);

        footer.child(smallBtn(compact ? "ALL" : "CLAIM ALL", compact ? 50 : 110, s(32),
                C_AMBER_DK, C_AMBER, 0xFFF5C870,
                b -> ClientPlayNetworking.send(new ClaimAllInboxC2SPacket())));
        return footer;
    }

    // ─── Content ─────────────────────────────────────────────────────────────
    private void refreshUi() {
        contentArea.clearChildren();
        List<PendingDelivery> deliveries = InboxClientState.getDeliveries();

        if (countLabel != null)
            countLabel.text(Component.literal(deliveries.size() + (deliveries.size() == 1 ? " item" : " items")));

        int totalPages = Math.max(1, (deliveries.size() + rowsPerPage - 1) / rowsPerPage);
        if (currentPage >= totalPages) currentPage = totalPages - 1;
        if (currentPage < 0) currentPage = 0;

        if (prevButton != null) prevButton.active(currentPage > 0);
        if (nextButton != null) nextButton.active(currentPage < totalPages - 1);
        if (pageLabel != null)
            pageLabel.text(Component.literal("PAGE  " + String.format("%02d", currentPage + 1) + " / " + String.format("%02d", totalPages)));
        rebuildPageBars(totalPages, currentPage);

        if (deliveries.isEmpty()) {
            buildEmpty();
            return;
        }

        // Column header
        FlowLayout colH = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        colH.surface(Surface.flat(0xFF172433));
        colH.padding(Insets.of(4, s(8), 4, s(8)));
        colH.gap(8);
        colH.child(cellLabel("ITEM", compact ? 180 : 260, C_INK_DIM));
        colH.child(cellLabel("TYPE", 90, C_INK_DIM));
        colH.child(Containers.horizontalFlow(Sizing.expand(), Sizing.content()));
        colH.child(cellLabelR("EXPIRES IN", 90, C_INK_DIM));
        colH.child(cellLabelR("COLLECT", 72, C_INK_DIM));
        contentArea.child(colH);

        // Paginated rows
        long now = System.currentTimeMillis() / 1000L;
        int start = currentPage * rowsPerPage;
        int end = Math.min(start + rowsPerPage, deliveries.size());
        for (int i = start; i < end; i++)
            contentArea.child(buildRow(deliveries.get(i), i, now));
    }

    private void rebuildPageBars(int total, int cur) {
        if (pageBars == null) return;
        pageBars.clearChildren();
        int containerW = 100, gap = 2;
        int usable = Math.max(total, containerW - gap * Math.max(0, total - 1));
        int base = usable / total, rem = usable % total;
        for (int i = 0; i < total; i++) {
            var bar = Components.box(Sizing.fixed(base + (i < rem ? 1 : 0)), Sizing.fill(100));
            bar.fill(true);
            bar.color(i == cur ? Color.ofRgb(0xF5B042) : Color.ofRgb(0x3B4D61));
            pageBars.child(bar);
        }
    }

    private void buildEmpty() {
        FlowLayout empty = Containers.verticalFlow(Sizing.fill(100), Sizing.expand());
        empty.surface(Surface.flat(C_ROW_A));
        empty.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        empty.gap(10);

        // Inbox icon placeholder
        FlowLayout iconBox = Containers.verticalFlow(Sizing.fixed(64), Sizing.fixed(64));
        iconBox.surface(Surface.flat(0xFF131E2B).and(Surface.outline(C_HAIR)));
        iconBox.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        iconBox.child(tint("✉", C_INK_DIM));
        empty.child(iconBox);

        empty.child(tint("Your inbox is empty.", C_INK_MID));
        empty.child(tint("Items won at auction or from cancelled listings appear here.", C_INK_DIM));
        contentArea.child(empty);
    }

    private FlowLayout buildRow(PendingDelivery delivery, int idx, long now) {
        int bg = idx % 2 == 0 ? C_ROW_A : C_ROW_B;
        long remaining = delivery.expiresAt() - now;
        boolean expiringSoon = remaining < 21600;   // < 6 hours
        boolean expiringVSoon = remaining < 3600;   // < 1 hour

        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(s(52)));
        row.surface(Surface.flat(bg));
        row.padding(Insets.of(s(6), s(8), s(6), s(8)));
        row.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        row.gap(8);

        // Item / LC display
        int itemW = compact ? 180 : 260;
        FlowLayout itemCell = Containers.horizontalFlow(Sizing.fixed(itemW), Sizing.content());
        itemCell.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        itemCell.gap(8);

        boolean isItem = delivery.itemStack() != null && !delivery.itemStack().isEmpty();
        if (isItem) {
            itemCell.child(Components.item(delivery.itemStack().copyWithCount(delivery.quantity()))
                    .showOverlay(true).setTooltipFromStack(true));
            FlowLayout txt = Containers.verticalFlow(Sizing.expand(), Sizing.content());
            txt.gap(1);
            txt.child(tint(delivery.itemStack().getHoverName().getString()
                    + (delivery.quantity() > 1 ? " ×" + delivery.quantity() : ""), C_INK));
            if (!compact) txt.child(tint(delivery.itemStack().getItem().toString(), C_INK_DIM));
            itemCell.child(txt);
        } else {
            // LC delivery
            FlowLayout lcBox = Containers.verticalFlow(Sizing.fixed(32), Sizing.fixed(32));
            lcBox.surface(Surface.flat(0xFF1A2A10).and(Surface.outline(0xFF3A5A20)));
            lcBox.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
            lcBox.child(tint("₴", C_GREEN));
            itemCell.child(lcBox);
            FlowLayout txt = Containers.verticalFlow(Sizing.expand(), Sizing.content());
            txt.gap(1);
            txt.child(tint(String.format("%,d", delivery.lcAmount() != null ? delivery.lcAmount() : 0) + " LC", C_GREEN));
            txt.child(tint("Coins", C_INK_DIM));
            itemCell.child(txt);
        }
        row.child(itemCell);

        // Reason label
        ReasonDisplay reasonDisplay = resolveReason(delivery.reason());
        row.child(cellLabel(reasonDisplay.label(), 110, reasonDisplay.color()));

        row.child(Containers.horizontalFlow(Sizing.expand(), Sizing.content()));

        // Time remaining
        int timeColor = expiringVSoon ? C_RED : (expiringSoon ? C_AMBER : C_INK_MID);
        row.child(cellLabelR(formatTime(remaining), 90, timeColor));

        // Collect button
        long deliveryId = delivery.id();
        ButtonComponent collectBtn = Components.button(Component.literal("COLLECT"), b ->
                ClientPlayNetworking.send(new ClaimInboxItemC2SPacket(deliveryId)));
        collectBtn.sizing(Sizing.fixed(s(70)), Sizing.fixed(s(26)));
        collectBtn.renderer((ctx, rendered, delta) -> {
            int col = rendered.isHoveredOrFocused() ? C_GREEN : 0xFF1A3A1A;
            int bord = C_GREEN;
            ctx.fill(rendered.getX(), rendered.getY(), rendered.getX() + rendered.getWidth(), rendered.getY() + rendered.getHeight(), col);
            ctx.drawRectOutline(rendered.getX(), rendered.getY(), rendered.getWidth(), rendered.getHeight(), bord);
        });
        row.child(collectBtn);

        // Expiry warning stripe on left edge if soon
        if (expiringSoon) {
            final int stripeColor = expiringVSoon ? C_RED : C_AMBER;
            row.surface(Surface.flat(bg).and((ctx, comp) ->
                    ctx.fill(comp.x(), comp.y(), comp.x() + 3, comp.y() + comp.height(), stripeColor)));
        }

        return row;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────
    private static String formatTime(long sec) {
        if (sec <= 0) return "EXPIRED";
        if (sec < 60) return sec + "s";
        if (sec < 3600) return (sec / 60) + "m";
        if (sec < 86400) return (sec / 3600) + "h " + ((sec % 3600) / 60) + "m";
        return (sec / 86400) + "d " + ((sec % 86400) / 3600) + "h";
    }

    private int s(int v) { return compact ? Math.max(v * 2 / 3, 12) : v; }

    private FlowLayout tag(String text, int color) {
        FlowLayout t = Containers.verticalFlow(Sizing.content(), Sizing.content());
        t.surface(Surface.flat(color & 0x00FFFFFF | 0x22000000).and(Surface.outline(color)));
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

    private record ReasonDisplay(String label, int color) {}

    private ReasonDisplay resolveReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return new ReasonDisplay("—", C_INK_DIM);
        }
        return switch (reason.toUpperCase()) {
            case "AUCTION_WON_ITEM"       -> translated("economy_ld.inbox.source.auction_won",       C_AMBER);
            case "AUCTION_BUYOUT_ITEM"    -> translated("economy_ld.inbox.source.auction_bought",    C_AMBER);
            case "AUCTION_SOLD_PAYOUT"    -> translated("economy_ld.inbox.source.auction_sold",      C_GREEN);
            case "AUCTION_EXPIRED_RETURN" -> translated("economy_ld.inbox.source.auction_expired",   C_RED);
            case "AUCTION_CANCELLED_RETURN" -> translated("economy_ld.inbox.source.auction_cancelled", C_INK_MID);
            case "DUEL_WIN"               -> translated("economy_ld.inbox.source.duel_win",          C_GREEN);
            case "DUEL_DRAW_REFUND"       -> translated("economy_ld.inbox.source.duel_draw",         C_INK_MID);
            case "DUNGEON_LOOT"           -> translated("economy_ld.inbox.source.dungeon_loot",      C_AMBER);
            case "DUNGEON_REWARD"         -> translated("economy_ld.inbox.source.dungeon_reward",    C_GREEN);
            case "EXTERNAL_DELIVERY"      -> translated("economy_ld.inbox.source.external",          C_INK_DIM);
            default -> new ReasonDisplay(reason, C_INK_DIM);
        };
    }

    private ReasonDisplay translated(String key, int color) {
        return new ReasonDisplay(Component.translatable(key).getString(), color);
    }

    private LabelComponent cellLabelR(String text, int width, int color) {
        LabelComponent l = cellLabel(text, width, color);
        l.horizontalTextAlignment(HorizontalAlignment.RIGHT);
        return l;
    }

    private ButtonComponent smallBtn(String text, int w, int h, int fill, int hover, int border,
                                     java.util.function.Consumer<ButtonComponent> onPress) {
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