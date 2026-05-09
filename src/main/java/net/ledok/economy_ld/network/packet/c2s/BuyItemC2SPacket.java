package net.ledok.economy_ld.network.packet.c2s;

import net.ledok.economy_ld.EconomyLdMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record BuyItemC2SPacket(UUID listingId, int quantity) implements CustomPacketPayload {
    public static final Type<BuyItemC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EconomyLdMod.MOD_ID, "buy_item")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, BuyItemC2SPacket> CODEC = StreamCodec.of(
            BuyItemC2SPacket::write,
            BuyItemC2SPacket::read
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buf, BuyItemC2SPacket payload) {
        buf.writeUUID(payload.listingId);
        buf.writeInt(payload.quantity);
    }

    private static BuyItemC2SPacket read(RegistryFriendlyByteBuf buf) {
        return new BuyItemC2SPacket(buf.readUUID(), buf.readInt());
    }
}
