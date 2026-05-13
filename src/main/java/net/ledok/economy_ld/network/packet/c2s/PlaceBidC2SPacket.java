package net.ledok.economy_ld.network.packet.c2s;

import net.ledok.economy_ld.EconomyLdMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record PlaceBidC2SPacket(UUID auctionId, long bidAmount) implements CustomPacketPayload {
    public static final Type<PlaceBidC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EconomyLdMod.MOD_ID, "place_bid")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, PlaceBidC2SPacket> CODEC = StreamCodec.of(
            PlaceBidC2SPacket::write,
            PlaceBidC2SPacket::read
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buf, PlaceBidC2SPacket payload) {
        buf.writeUUID(payload.auctionId);
        buf.writeLong(payload.bidAmount);
    }

    private static PlaceBidC2SPacket read(RegistryFriendlyByteBuf buf) {
        return new PlaceBidC2SPacket(buf.readUUID(), buf.readLong());
    }
}
