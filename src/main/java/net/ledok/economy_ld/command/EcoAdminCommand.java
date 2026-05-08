package net.ledok.economy_ld.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.ledok.economy_ld.manager.EconomyManager;
import net.ledok.economy_ld.util.CurrencyFormatter;
import net.ledok.economy_ld.util.OfflinePlayerResolver;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public final class EcoAdminCommand {
    private EcoAdminCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("eco")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("give")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                        .executes(ctx -> give(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "player"),
                                                LongArgumentType.getLong(ctx, "amount")
                                        )))))
                .then(Commands.literal("take")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                        .executes(ctx -> take(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "player"),
                                                LongArgumentType.getLong(ctx, "amount")
                                        )))))
                .then(Commands.literal("set")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                        .executes(ctx -> set(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "player"),
                                                LongArgumentType.getLong(ctx, "amount")
                                        )))))
                .then(Commands.literal("balance")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(ctx -> balance(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "player")
                                ))))
                .then(Commands.literal("reload")
                        .executes(ctx -> reload(ctx.getSource()))));
    }

    private static int give(CommandSourceStack source, String username, long amount) {
        UUID uuid = OfflinePlayerResolver.resolveUuid(source.getServer(), username);
        EconomyManager.getInstance().give(uuid, username, amount)
                .whenComplete((ignored, error) -> source.getServer().execute(() -> {
                    if (error != null) {
                        source.sendFailure(Component.literal("Failed to give funds: " + error.getCause().getMessage()));
                        return;
                    }
                    source.sendSuccess(() -> Component.literal("Gave " + CurrencyFormatter.format(amount) + " to " + username + "."), true);
                }));
        return 1;
    }

    private static int take(CommandSourceStack source, String username, long amount) {
        UUID uuid = OfflinePlayerResolver.resolveUuid(source.getServer(), username);
        EconomyManager.getInstance().take(uuid, username, amount)
                .whenComplete((success, error) -> source.getServer().execute(() -> {
                    if (error != null) {
                        source.sendFailure(Component.literal("Failed to take funds: " + error.getCause().getMessage()));
                        return;
                    }
                    if (!success) {
                        source.sendFailure(Component.literal("Insufficient funds for " + username + "."));
                        return;
                    }
                    source.sendSuccess(() -> Component.literal("Took " + CurrencyFormatter.format(amount) + " from " + username + "."), true);
                }));
        return 1;
    }

    private static int set(CommandSourceStack source, String username, long amount) {
        UUID uuid = OfflinePlayerResolver.resolveUuid(source.getServer(), username);
        EconomyManager.getInstance().set(uuid, username, amount)
                .whenComplete((ignored, error) -> source.getServer().execute(() -> {
                    if (error != null) {
                        source.sendFailure(Component.literal("Failed to set balance: " + error.getCause().getMessage()));
                        return;
                    }
                    source.sendSuccess(() -> Component.literal("Set " + username + "'s balance to " + CurrencyFormatter.format(amount) + "."), true);
                }));
        return 1;
    }

    private static int balance(CommandSourceStack source, String username) {
        EconomyManager.getInstance().getBalanceByUsername(username)
                .whenComplete((balanceOpt, error) -> source.getServer().execute(() -> {
                    if (error != null) {
                        source.sendFailure(Component.literal("Failed to read balance for " + username + "."));
                        return;
                    }
                    if (balanceOpt.isEmpty()) {
                        source.sendFailure(Component.literal("Player '" + username + "' has no wallet record."));
                        return;
                    }
                    source.sendSuccess(() -> Component.literal(
                            username + "'s balance: " + CurrencyFormatter.format(balanceOpt.getAsLong())
                    ), false);
                }));
        return 1;
    }

    private static int reload(CommandSourceStack source) {
        EconomyManager.getInstance().reloadConfig()
                .whenComplete((ignored, error) -> source.getServer().execute(() -> {
                    if (error != null) {
                        source.sendFailure(Component.literal("Config reload failed."));
                        return;
                    }
                    source.sendSuccess(() -> Component.literal("Economy config reloaded."), true);
                }));
        return 1;
    }
}
