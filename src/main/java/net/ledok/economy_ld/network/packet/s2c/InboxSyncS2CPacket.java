package net.ledok.economy_ld.network.packet.s2c;

import net.ledok.economy_ld.EconomyLdMod;
import net.ledok.economy_ld.auction.PendingDelivery;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public record InboxSyncS2CPacket(List<PendingDelivery> deliveries) implements CustomPacketPayload {
    public static final Type<InboxSyncS2CPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EconomyLdMod.MOD_ID, "inbox_sync")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, InboxSyncS2CPacket> CODEC = StreamCodec.of(
            InboxSyncS2CPacket::write,
            InboxSyncS2CPacket::read
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buf, InboxSyncS2CPacket payload) {
        buf.writeInt(payload.deliveries.size());
        for (PendingDelivery delivery : payload.deliveries) {
            buf.writeLong(delivery.id());
            if (delivery.itemStack() != null && !delivery.itemStack().isEmpty()
                    && delivery.itemStack().saveOptional(buf.registryAccess()) instanceof CompoundTag tag) {
                buf.writeBoolean(true);
                buf.writeNbt(tag);
            } else {
                buf.writeBoolean(false);
            }
            buf.writeInt(delivery.quantity());
            writeNullableLong(buf, delivery.lcAmount());
            buf.writeUtf(delivery.reason() == null ? "" : delivery.reason(), 32);
            buf.writeLong(delivery.expiresAt());
        }
    }

    private static InboxSyncS2CPacket read(RegistryFriendlyByteBuf buf) {
        int size = buf.readInt();
        List<PendingDelivery> deliveries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            long id = buf.readLong();
            ItemStack item = ItemStack.EMPTY;
            if (buf.readBoolean()) {
                CompoundTag itemNbt = buf.readNbt();
                item = itemNbt == null ? ItemStack.EMPTY : ItemStack.parseOptional(buf.registryAccess(), itemNbt);
            }
            int quantity = buf.readInt();
            Long lcAmount = readNullableLong(buf);
            String reason = buf.readUtf(32);
            long expiresAt = buf.readLong();
            deliveries.add(new PendingDelivery(id, null, item, quantity, lcAmount, reason, expiresAt));
        }
        return new InboxSyncS2CPacket(deliveries);
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
