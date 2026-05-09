package net.ledok.economy_ld.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.ledok.economy_ld.manager.EconomyManager;
import net.ledok.economy_ld.util.CurrencyFormatter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

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
        resolveKnownPlayer(source, username).thenCompose(target -> EconomyManager.getInstance().give(target.uuid(), target.username(), amount))
                .whenComplete((ignored, error) -> source.getServer().execute(() -> {
                    if (error != null) {
                        if (isUnknownPlayer(error)) {
                            source.sendFailure(Component.literal("Player '" + username + "' has never joined this server."));
                        } else {
                            source.sendFailure(Component.literal("Failed to give funds: " + errorMessage(error)));
                        }
                        return;
                    }
                    source.sendSuccess(() -> Component.literal("Gave " + CurrencyFormatter.format(amount) + " to " + username + "."), true);
                }));
        return 1;
    }

    private static int take(CommandSourceStack source, String username, long amount) {
        resolveKnownPlayer(source, username).thenCompose(target -> EconomyManager.getInstance().take(target.uuid(), target.username(), amount))
                .whenComplete((success, error) -> source.getServer().execute(() -> {
                    if (error != null) {
                        if (isUnknownPlayer(error)) {
                            source.sendFailure(Component.literal("Player '" + username + "' has never joined this server."));
                        } else {
                            source.sendFailure(Component.literal("Failed to take funds: " + errorMessage(error)));
                        }
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
        resolveKnownPlayer(source, username).thenCompose(target -> EconomyManager.getInstance().set(target.uuid(), target.username(), amount))
                .whenComplete((ignored, error) -> source.getServer().execute(() -> {
                    if (error != null) {
                        if (isUnknownPlayer(error)) {
                            source.sendFailure(Component.literal("Player '" + username + "' has never joined this server."));
                        } else {
                            source.sendFailure(Component.literal("Failed to set balance: " + errorMessage(error)));
                        }
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

    private static CompletableFuture<ResolvedPlayer> resolveKnownPlayer(CommandSourceStack source, String username) {
        ServerPlayer onlineMatch = source.getServer().getPlayerList().getPlayers().stream()
                .filter(player -> player.getName().getString().equalsIgnoreCase(username))
                .findFirst()
                .orElse(null);
        if (onlineMatch != null) {
            return CompletableFuture.completedFuture(new ResolvedPlayer(onlineMatch.getUUID(), onlineMatch.getName().getString()));
        }

        return EconomyManager.getInstance().getUuidByUsername(username).thenCompose(uuidOpt -> {
            if (uuidOpt.isEmpty()) {
                return CompletableFuture.failedFuture(new IllegalArgumentException("UNKNOWN_PLAYER:" + username));
            }
            return EconomyManager.getInstance().getUsernameByUuid(uuidOpt.get())
                    .thenApply(nameOpt -> new ResolvedPlayer(uuidOpt.get(), nameOpt.orElse(username)));
        });
    }

    private static boolean isUnknownPlayer(Throwable error) {
        String message = errorMessage(error);
        return message != null && message.startsWith("UNKNOWN_PLAYER:");
    }

    private static String errorMessage(Throwable error) {
        if (error instanceof CompletionException && error.getCause() != null) {
            return error.getCause().getMessage();
        }
        return error.getMessage();
    }

    private record ResolvedPlayer(UUID uuid, String username) {
    }
}
