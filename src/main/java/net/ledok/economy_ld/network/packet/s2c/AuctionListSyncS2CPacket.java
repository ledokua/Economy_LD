package net.ledok.economy_ld.network.packet.s2c;

import net.ledok.economy_ld.EconomyLdMod;
import net.ledok.economy_ld.auction.AuctionRecord;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record AuctionListSyncS2CPacket(List<AuctionRecord> auctions) implements CustomPacketPayload {
    public static final Type<AuctionListSyncS2CPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EconomyLdMod.MOD_ID, "auction_list_sync")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, AuctionListSyncS2CPacket> CODEC = StreamCodec.of(
            AuctionListSyncS2CPacket::write,
            AuctionListSyncS2CPacket::read
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buf, AuctionListSyncS2CPacket payload) {
        buf.writeInt(payload.auctions.size());
        for (AuctionRecord auction : payload.auctions) {
            buf.writeUUID(auction.id());
            buf.writeUUID(auction.sellerUuid());
            buf.writeUtf(auction.sellerName(), 64);
            CompoundTag itemNbt = auction.itemStack().saveOptional(buf.registryAccess()) instanceof CompoundTag tag
                    ? tag
                    : new CompoundTag();
            buf.writeNbt(itemNbt);
            buf.writeInt(auction.quantity());
            buf.writeLong(auction.startPrice());
            writeNullableLong(buf, auction.buyoutPrice());
            buf.writeLong(auction.currentBid());
            writeNullableUuid(buf, auction.bidderUuid());
            writeNullableString(buf, auction.bidderName());
            buf.writeLong(auction.expiresAt());
            buf.writeUtf(auction.status(), 16);
        }
    }

    private static AuctionListSyncS2CPacket read(RegistryFriendlyByteBuf buf) {
        int size = buf.readInt();
        List<AuctionRecord> auctions = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            UUID id = buf.readUUID();
            UUID sellerUuid = buf.readUUID();
            String sellerName = buf.readUtf(64);
            CompoundTag itemNbt = buf.readNbt();
            ItemStack stack = itemNbt == null ? ItemStack.EMPTY : ItemStack.parseOptional(buf.registryAccess(), itemNbt);
            int quantity = buf.readInt();
            long startPrice = buf.readLong();
            Long buyoutPrice = readNullableLong(buf);
            long currentBid = buf.readLong();
            UUID bidderUuid = readNullableUuid(buf);
            String bidderName = readNullableString(buf);
            long expiresAt = buf.readLong();
            String status = buf.readUtf(16);
            auctions.add(new AuctionRecord(
                    id,
                    sellerUuid,
                    sellerName,
                    stack,
                    quantity,
                    startPrice,
                    buyoutPrice,
                    currentBid,
                    bidderUuid,
                    bidderName,
                    expiresAt,
                    status
            ));
        }
        return new AuctionListSyncS2CPacket(auctions);
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

    private static void writeNullableUuid(RegistryFriendlyByteBuf buf, UUID value) {
        buf.writeBoolean(value != null);
        if (value != null) {
            buf.writeUUID(value);
        }
    }

    private static UUID readNullableUuid(RegistryFriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readUUID() : null;
    }

    private static void writeNullableString(RegistryFriendlyByteBuf buf, String value) {
        buf.writeBoolean(value != null);
        if (value != null) {
            buf.writeUtf(value, 64);
        }
    }

    private static String readNullableString(RegistryFriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readUtf(64) : null;
    }
}
