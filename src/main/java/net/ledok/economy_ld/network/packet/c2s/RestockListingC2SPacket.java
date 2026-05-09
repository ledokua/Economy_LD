package net.ledok.economy_ld.network.packet.c2s;

import net.ledok.economy_ld.EconomyLdMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record RestockListingC2SPacket(UUID listingId, int quantity) implements CustomPacketPayload {
    public static final Type<RestockListingC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EconomyLdMod.MOD_ID, "restock_listing")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, RestockListingC2SPacket> CODEC = StreamCodec.of(
            RestockListingC2SPacket::write,
            RestockListingC2SPacket::read
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buf, RestockListingC2SPacket payload) {
        buf.writeUUID(payload.listingId);
        buf.writeInt(payload.quantity);
    }

    private static RestockListingC2SPacket read(RegistryFriendlyByteBuf buf) {
        return new RestockListingC2SPacket(buf.readUUID(), buf.readInt());
    }
}
