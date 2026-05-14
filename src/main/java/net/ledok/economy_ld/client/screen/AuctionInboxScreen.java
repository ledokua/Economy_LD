package net.ledok.economy_ld.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class AuctionInboxScreen extends Screen {
    public AuctionInboxScreen() {
        super(Component.literal("Auction Inbox"));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFE8EEF5);
        guiGraphics.drawCenteredString(this.font,
                Component.literal("Pending deliveries: " + InboxClientState.getDeliveries().size()),
                this.width / 2, 40, 0xFF9AA8B8);
        guiGraphics.drawCenteredString(this.font,
                Component.literal("Inbox UI WIP - use packet handlers to claim"),
                this.width / 2, 60, 0xFF9AA8B8);
    }
}
