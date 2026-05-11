package net.ledok.economy_ld.network.packet.s2c;

import net.ledok.economy_ld.EconomyLdMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ShopActionResultS2CPacket(
        ActionType actionType,
        String itemName,
        int quantity,
        long lcAmount,
        long playerBalance
) implements CustomPacketPayload {
    public static final Type<ShopActionResultS2CPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EconomyLdMod.MOD_ID, "shop_action_result")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ShopActionResultS2CPacket> CODEC = StreamCodec.of(
            ShopActionResultS2CPacket::write,
            ShopActionResultS2CPacket::read
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buf, ShopActionResultS2CPacket payload) {
        buf.writeEnum(payload.actionType);
        buf.writeUtf(payload.itemName, 64);
        buf.writeInt(payload.quantity);
        buf.writeLong(payload.lcAmount);
        buf.writeLong(payload.playerBalance);
    }

    private static ShopActionResultS2CPacket read(RegistryFriendlyByteBuf buf) {
        ActionType type = buf.readEnum(ActionType.class);
        String itemName = buf.readUtf(64);
        int quantity = buf.readInt();
        long lcAmount = buf.readLong();
        long playerBalance = buf.readLong();
        return new ShopActionResultS2CPacket(type, itemName, quantity, lcAmount, playerBalance);
    }

    public enum ActionType {
        BOUGHT,
        SOLD,
        RESTOCKED,
        INSUFFICIENT_FUNDS,
        SHOP_FULL,
        OUT_OF_STOCK
    }
}
