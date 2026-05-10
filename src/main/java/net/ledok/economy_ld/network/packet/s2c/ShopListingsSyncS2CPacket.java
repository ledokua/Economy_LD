package net.ledok.economy_ld.network.packet.s2c;

import net.ledok.economy_ld.EconomyLdMod;
import net.ledok.economy_ld.shop.ShopListing;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record ShopListingsSyncS2CPacket(
        UUID shopId,
        boolean adminShop,
        boolean ownerOrOperator,
        String ownerLabel,
        long openerBalance,
        List<ShopListing> listings
) implements CustomPacketPayload {
    public static final Type<ShopListingsSyncS2CPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EconomyLdMod.MOD_ID, "shop_listings_sync")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ShopListingsSyncS2CPacket> CODEC = StreamCodec.of(
            ShopListingsSyncS2CPacket::write,
            ShopListingsSyncS2CPacket::read
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buf, ShopListingsSyncS2CPacket payload) {
        buf.writeUUID(payload.shopId);
        buf.writeBoolean(payload.adminShop);
        buf.writeBoolean(payload.ownerOrOperator);
        buf.writeUtf(payload.ownerLabel, 64);
        buf.writeLong(payload.openerBalance);
        buf.writeInt(payload.listings.size());
        for (ShopListing listing : payload.listings) {
            buf.writeUUID(listing.id());
            buf.writeUUID(listing.shopId());
            CompoundTag itemNbt = listing.itemStack().saveOptional(buf.registryAccess()) instanceof CompoundTag tag
                    ? tag
                    : new CompoundTag();
            buf.writeNbt(itemNbt);
            writeNullableLong(buf, listing.priceBuy());
            writeNullableLong(buf, listing.priceSell());
            buf.writeInt(listing.perOp());
            writeNullableLong(buf, listing.buyCap());
            writeNullableLong(buf, listing.stock());
        }
    }

    private static ShopListingsSyncS2CPacket read(RegistryFriendlyByteBuf buf) {
        UUID shopId = buf.readUUID();
        boolean adminShop = buf.readBoolean();
        boolean ownerOrOperator = buf.readBoolean();
        String ownerLabel = buf.readUtf(64);
        long openerBalance = buf.readLong();
        int size = buf.readInt();
        List<ShopListing> listings = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            UUID id = buf.readUUID();
            UUID packetShopId = buf.readUUID();
            CompoundTag itemNbt = buf.readNbt();
            ItemStack stack = itemNbt == null ? ItemStack.EMPTY : ItemStack.parseOptional(buf.registryAccess(), itemNbt);
            Long priceBuy = readNullableLong(buf);
            Long priceSell = readNullableLong(buf);
            int perOp = buf.readInt();
            Long buyCap = readNullableLong(buf);
            Long stock = readNullableLong(buf);
            listings.add(new ShopListing(id, packetShopId, stack, priceBuy, priceSell, perOp, buyCap, stock));
        }
        return new ShopListingsSyncS2CPacket(shopId, adminShop, ownerOrOperator, ownerLabel, openerBalance, listings);
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
