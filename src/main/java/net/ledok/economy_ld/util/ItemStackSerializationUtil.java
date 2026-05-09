package net.ledok.economy_ld.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public final class ItemStackSerializationUtil {
    private ItemStackSerializationUtil() {
    }

    public static String toBase64(ItemStack stack) {
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("Cannot serialize empty ItemStack");
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        tag.putInt("count", stack.getCount());

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            NbtIo.writeCompressed(tag, output);
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize ItemStack", e);
        }
    }

    public static ItemStack fromBase64(String encoded) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(Base64.getDecoder().decode(encoded))) {
            CompoundTag tag = NbtIo.readCompressed(input, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            ResourceLocation itemId = ResourceLocation.parse(tag.getString("id"));
            Item item = BuiltInRegistries.ITEM.get(itemId);
            int count = tag.getInt("count");
            if (item == null || count <= 0) {
                return ItemStack.EMPTY;
            }
            return new ItemStack(item, count);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize ItemStack", e);
        }
    }
}
