package net.ledok.economy_ld.network.packet.s2c;

import net.ledok.economy_ld.EconomyLdMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AuctionActionResultS2CPacket(
        ActionType actionType,
        String itemName,
        long lcAmount
) implements CustomPacketPayload {
    public static final Type<AuctionActionResultS2CPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EconomyLdMod.MOD_ID, "auction_action_result")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, AuctionActionResultS2CPacket> CODEC = StreamCodec.of(
            AuctionActionResultS2CPacket::write,
            AuctionActionResultS2CPacket::read
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buf, AuctionActionResultS2CPacket payload) {
        buf.writeEnum(payload.actionType);
        buf.writeUtf(payload.itemName, 64);
        buf.writeLong(payload.lcAmount);
    }

    private static AuctionActionResultS2CPacket read(RegistryFriendlyByteBuf buf) {
        ActionType type = buf.readEnum(ActionType.class);
        String itemName = buf.readUtf(64);
        long lcAmount = buf.readLong();
        return new AuctionActionResultS2CPacket(type, itemName, lcAmount);
    }

    public enum ActionType {
        LISTED,
        BID_PLACED,
        OUTBID,
        BUYOUT,
        CANCELLED,
        INSUFFICIENT_FUNDS,
        NOT_ENOUGH_ITEMS,
        LIMIT_REACHED,
        ALREADY_ENDED
    }
}
