package net.ledok.economy_ld.network.packet.s2c;

import net.ledok.economy_ld.EconomyLdMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenAuctionScreenS2CPacket() implements CustomPacketPayload {
    public static final Type<OpenAuctionScreenS2CPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EconomyLdMod.MOD_ID, "open_auction_screen")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenAuctionScreenS2CPacket> CODEC = StreamCodec.of(
            OpenAuctionScreenS2CPacket::write,
            OpenAuctionScreenS2CPacket::read
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buf, OpenAuctionScreenS2CPacket payload) {
    }

    private static OpenAuctionScreenS2CPacket read(RegistryFriendlyByteBuf buf) {
        return new OpenAuctionScreenS2CPacket();
    }
}
