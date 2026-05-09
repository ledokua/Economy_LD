package net.ledok.economy_ld.network.packet.c2s;

import net.ledok.economy_ld.EconomyLdMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record RemoveListingC2SPacket(UUID listingId) implements CustomPacketPayload {
    public static final Type<RemoveListingC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EconomyLdMod.MOD_ID, "remove_listing")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, RemoveListingC2SPacket> CODEC = StreamCodec.of(
            RemoveListingC2SPacket::write,
            RemoveListingC2SPacket::read
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buf, RemoveListingC2SPacket payload) {
        buf.writeUUID(payload.listingId);
    }

    private static RemoveListingC2SPacket read(RegistryFriendlyByteBuf buf) {
        return new RemoveListingC2SPacket(buf.readUUID());
    }
}
