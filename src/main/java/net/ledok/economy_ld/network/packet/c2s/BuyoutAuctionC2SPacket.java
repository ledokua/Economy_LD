package net.ledok.economy_ld.network.packet.c2s;

import net.ledok.economy_ld.EconomyLdMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record BuyoutAuctionC2SPacket(UUID auctionId) implements CustomPacketPayload {
    public static final Type<BuyoutAuctionC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EconomyLdMod.MOD_ID, "buyout_auction")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, BuyoutAuctionC2SPacket> CODEC = StreamCodec.of(
            BuyoutAuctionC2SPacket::write,
            BuyoutAuctionC2SPacket::read
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buf, BuyoutAuctionC2SPacket payload) {
        buf.writeUUID(payload.auctionId);
    }

    private static BuyoutAuctionC2SPacket read(RegistryFriendlyByteBuf buf) {
        return new BuyoutAuctionC2SPacket(buf.readUUID());
    }
}
