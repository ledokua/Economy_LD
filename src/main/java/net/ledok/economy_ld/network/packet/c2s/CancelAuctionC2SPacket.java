package net.ledok.economy_ld.network.packet.c2s;

import net.ledok.economy_ld.EconomyLdMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record CancelAuctionC2SPacket(UUID auctionId) implements CustomPacketPayload {
    public static final Type<CancelAuctionC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EconomyLdMod.MOD_ID, "cancel_auction")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CancelAuctionC2SPacket> CODEC = StreamCodec.of(
            CancelAuctionC2SPacket::write,
            CancelAuctionC2SPacket::read
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buf, CancelAuctionC2SPacket payload) {
        buf.writeUUID(payload.auctionId);
    }

    private static CancelAuctionC2SPacket read(RegistryFriendlyByteBuf buf) {
        return new CancelAuctionC2SPacket(buf.readUUID());
    }
}
