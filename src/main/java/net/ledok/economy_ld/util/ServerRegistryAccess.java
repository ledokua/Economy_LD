package net.ledok.economy_ld.util;

import net.minecraft.core.HolderLookup;

public final class ServerRegistryAccess {
    private static volatile HolderLookup.Provider registryAccess;

    private ServerRegistryAccess() {
    }

    public static void set(HolderLookup.Provider value) {
        registryAccess = value;
    }

    public static void clear() {
        registryAccess = null;
    }

    public static HolderLookup.Provider require() {
        if (registryAccess == null) {
            throw new IllegalStateException("Server registry access is not initialized");
        }
        return registryAccess;
    }
}
