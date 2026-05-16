package net.ledok.economy_ld.util;

import net.fabricmc.loader.api.FabricLoader;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

public final class PermissionHelper {
    private static final boolean LUCKPERMS_PRESENT =
            FabricLoader.getInstance().isModLoaded("luckperms");

    private PermissionHelper() {
    }

    /**
     * Check a permission node with LuckPerms if available,
     * falling back to op level check if not.
     */
    public static boolean check(CommandSourceStack source, String node, int fallbackOpLevel) {
        if (LUCKPERMS_PRESENT) {
            try {
                ServerPlayer player = source.getPlayer();
                if (player == null) {
                    return source.hasPermission(fallbackOpLevel);
                }
                return checkLuckPerms(player, node);
            } catch (Exception e) {
                // LuckPerms not fully initialized yet — fall back
            }
        }
        return source.hasPermission(fallbackOpLevel);
    }

    /**
     * Check a permission node for a ServerPlayer directly,
     * falling back to op level if LuckPerms not present.
     */
    public static boolean check(ServerPlayer player, String node, int fallbackOpLevel) {
        if (LUCKPERMS_PRESENT) {
            try {
                return checkLuckPerms(player, node);
            } catch (Exception e) {
                // fall back
            }
        }
        return player.hasPermissions(fallbackOpLevel);
    }

    private static boolean checkLuckPerms(ServerPlayer player, String node) {
        LuckPerms api = LuckPermsProvider.get();
        User user = api.getUserManager().getUser(player.getUUID());
        if (user == null) return false;
        return user.getCachedData()
                .getPermissionData(QueryOptions.defaultContextualOptions())
                .checkPermission(node)
                .asBoolean();
    }
}
