package net.ledok.economy_ld.network.packet.c2s;

import net.ledok.economy_ld.EconomyLdMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ClaimAllInboxC2SPacket() implements CustomPacketPayload {
    public static final Type<ClaimAllInboxC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EconomyLdMod.MOD_ID, "claim_all_inbox")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimAllInboxC2SPacket> CODEC = StreamCodec.of(
            ClaimAllInboxC2SPacket::write,
            ClaimAllInboxC2SPacket::read
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buf, ClaimAllInboxC2SPacket payload) {
    }

    private static ClaimAllInboxC2SPacket read(RegistryFriendlyByteBuf buf) {
        return new ClaimAllInboxC2SPacket();
    }
}
