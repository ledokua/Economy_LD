package net.ledok.economy_ld.network.packet.c2s;

import net.ledok.economy_ld.EconomyLdMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ClaimInboxItemC2SPacket(long deliveryId) implements CustomPacketPayload {
    public static final Type<ClaimInboxItemC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EconomyLdMod.MOD_ID, "claim_inbox_item")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimInboxItemC2SPacket> CODEC = StreamCodec.of(
            ClaimInboxItemC2SPacket::write,
            ClaimInboxItemC2SPacket::read
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buf, ClaimInboxItemC2SPacket payload) {
        buf.writeLong(payload.deliveryId);
    }

    private static ClaimInboxItemC2SPacket read(RegistryFriendlyByteBuf buf) {
        return new ClaimInboxItemC2SPacket(buf.readLong());
    }
}
