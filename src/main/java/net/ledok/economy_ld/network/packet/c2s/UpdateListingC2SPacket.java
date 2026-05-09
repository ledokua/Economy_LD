package net.ledok.economy_ld.network.packet.c2s;

import net.ledok.economy_ld.EconomyLdMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record UpdateListingC2SPacket(
        UUID listingId,
        Long priceBuy,
        Long priceSell,
        int perOp,
        Long buyCap
) implements CustomPacketPayload {
    public static final Type<UpdateListingC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EconomyLdMod.MOD_ID, "update_listing")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateListingC2SPacket> CODEC = StreamCodec.of(
            UpdateListingC2SPacket::write,
            UpdateListingC2SPacket::read
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buf, UpdateListingC2SPacket payload) {
        buf.writeUUID(payload.listingId);
        writeNullableLong(buf, payload.priceBuy);
        writeNullableLong(buf, payload.priceSell);
        buf.writeInt(payload.perOp);
        writeNullableLong(buf, payload.buyCap);
    }

    private static UpdateListingC2SPacket read(RegistryFriendlyByteBuf buf) {
        UUID listingId = buf.readUUID();
        Long priceBuy = readNullableLong(buf);
        Long priceSell = readNullableLong(buf);
        int perOp = buf.readInt();
        Long buyCap = readNullableLong(buf);
        return new UpdateListingC2SPacket(listingId, priceBuy, priceSell, perOp, buyCap);
    }

    private static void writeNullableLong(RegistryFriendlyByteBuf buf, Long value) {
        buf.writeBoolean(value != null);
        if (value != null) {
            buf.writeLong(value);
        }
    }

    private static Long readNullableLong(RegistryFriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readLong() : null;
    }
}
