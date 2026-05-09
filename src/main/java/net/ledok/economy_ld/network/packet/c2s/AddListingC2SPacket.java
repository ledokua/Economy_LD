package net.ledok.economy_ld.network.packet.c2s;

import net.ledok.economy_ld.EconomyLdMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record AddListingC2SPacket(
        UUID shopId,
        CompoundTag itemNbt,
        Long priceBuy,
        Long priceSell,
        int perOp,
        Long buyCap
) implements CustomPacketPayload {
    public static final Type<AddListingC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EconomyLdMod.MOD_ID, "add_listing")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, AddListingC2SPacket> CODEC = StreamCodec.of(
            AddListingC2SPacket::write,
            AddListingC2SPacket::read
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buf, AddListingC2SPacket payload) {
        buf.writeUUID(payload.shopId);
        buf.writeNbt(payload.itemNbt);
        writeNullableLong(buf, payload.priceBuy);
        writeNullableLong(buf, payload.priceSell);
        buf.writeInt(payload.perOp);
        writeNullableLong(buf, payload.buyCap);
    }

    private static AddListingC2SPacket read(RegistryFriendlyByteBuf buf) {
        UUID shopId = buf.readUUID();
        CompoundTag itemNbt = buf.readNbt();
        Long priceBuy = readNullableLong(buf);
        Long priceSell = readNullableLong(buf);
        int perOp = buf.readInt();
        Long buyCap = readNullableLong(buf);
        return new AddListingC2SPacket(shopId, itemNbt, priceBuy, priceSell, perOp, buyCap);
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
