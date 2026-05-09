package net.ledok.economy_ld.network.packet.c2s;

import net.ledok.economy_ld.EconomyLdMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record SellItemC2SPacket(UUID listingId, int quantity) implements CustomPacketPayload {
    public static final Type<SellItemC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EconomyLdMod.MOD_ID, "sell_item")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SellItemC2SPacket> CODEC = StreamCodec.of(
            SellItemC2SPacket::write,
            SellItemC2SPacket::read
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buf, SellItemC2SPacket payload) {
        buf.writeUUID(payload.listingId);
        buf.writeInt(payload.quantity);
    }

    private static SellItemC2SPacket read(RegistryFriendlyByteBuf buf) {
        return new SellItemC2SPacket(buf.readUUID(), buf.readInt());
    }
}
