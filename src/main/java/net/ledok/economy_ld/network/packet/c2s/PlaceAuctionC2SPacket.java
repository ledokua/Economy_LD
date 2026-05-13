package net.ledok.economy_ld.network.packet.c2s;

import net.ledok.economy_ld.EconomyLdMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PlaceAuctionC2SPacket(
        CompoundTag itemNbt,
        int quantity,
        long startPrice,
        Long buyoutPrice,
        long durationSeconds
) implements CustomPacketPayload {
    public static final Type<PlaceAuctionC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EconomyLdMod.MOD_ID, "place_auction")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, PlaceAuctionC2SPacket> CODEC = StreamCodec.of(
            PlaceAuctionC2SPacket::write,
            PlaceAuctionC2SPacket::read
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buf, PlaceAuctionC2SPacket payload) {
        buf.writeNbt(payload.itemNbt);
        buf.writeInt(payload.quantity);
        buf.writeLong(payload.startPrice);
        writeNullableLong(buf, payload.buyoutPrice);
        buf.writeLong(payload.durationSeconds);
    }

    private static PlaceAuctionC2SPacket read(RegistryFriendlyByteBuf buf) {
        CompoundTag itemNbt = buf.readNbt();
        int quantity = buf.readInt();
        long startPrice = buf.readLong();
        Long buyoutPrice = readNullableLong(buf);
        long durationSeconds = buf.readLong();
        return new PlaceAuctionC2SPacket(itemNbt, quantity, startPrice, buyoutPrice, durationSeconds);
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
