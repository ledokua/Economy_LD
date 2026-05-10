package net.ledok.economy_ld.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.item.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public final class ItemStackSerializationUtil {
    private ItemStackSerializationUtil() {
    }

    public static String toBase64(ItemStack stack) {
        return toBase64(stack, ServerRegistryAccess.require());
    }

    public static String toBase64(ItemStack stack, HolderLookup.Provider registryAccess) {
        if (registryAccess == null) {
            throw new IllegalStateException("Registry access is not available");
        }
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("Cannot serialize empty ItemStack");
        }
        if (!(stack.saveOptional(registryAccess) instanceof CompoundTag tag)) {
            throw new IllegalStateException("ItemStack did not serialize to a CompoundTag");
        }

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            NbtIo.writeCompressed(tag, output);
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize ItemStack", e);
        }
    }

    public static ItemStack fromBase64(String encoded) {
        return fromBase64(encoded, ServerRegistryAccess.require());
    }

    public static ItemStack fromBase64(String encoded, HolderLookup.Provider registryAccess) {
        if (registryAccess == null) {
            throw new IllegalStateException("Registry access is not available");
        }
        if (encoded == null || encoded.isBlank()) {
            return ItemStack.EMPTY;
        }

        try (ByteArrayInputStream input = new ByteArrayInputStream(Base64.getDecoder().decode(encoded))) {
            CompoundTag tag = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
            if (tag == null) {
                return ItemStack.EMPTY;
            }
            return ItemStack.parseOptional(registryAccess, tag);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize ItemStack", e);
        }
    }
}
