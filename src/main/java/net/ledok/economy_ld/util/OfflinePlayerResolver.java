package net.ledok.economy_ld.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class OfflinePlayerResolver {
    private OfflinePlayerResolver() {
    }

    public static UUID resolveUuid(MinecraftServer server, String username) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(username);
        if (online != null) {
            return online.getUUID();
        }
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    }
}
