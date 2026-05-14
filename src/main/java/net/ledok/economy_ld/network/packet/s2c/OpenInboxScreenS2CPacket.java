package net.ledok.economy_ld.network.packet.s2c;

import net.ledok.economy_ld.EconomyLdMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenInboxScreenS2CPacket() implements CustomPacketPayload {
    public static final Type<OpenInboxScreenS2CPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EconomyLdMod.MOD_ID, "open_inbox_screen")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenInboxScreenS2CPacket> CODEC = StreamCodec.of(
            OpenInboxScreenS2CPacket::write,
            OpenInboxScreenS2CPacket::read
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buf, OpenInboxScreenS2CPacket payload) {
    }

    private static OpenInboxScreenS2CPacket read(RegistryFriendlyByteBuf buf) {
        return new OpenInboxScreenS2CPacket();
    }
}
