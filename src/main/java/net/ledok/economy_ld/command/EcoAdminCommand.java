package net.ledok.economy_ld.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.ledok.economy_ld.manager.EconomyManager;
import net.ledok.economy_ld.util.CurrencyFormatter;
import net.ledok.economy_ld.util.PermissionHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class EcoAdminCommand {
    private static final SuggestionProvider<CommandSourceStack> KNOWN_PLAYERS = (context, builder) -> {
        context.getSource().getServer().getPlayerList().getPlayers()
                .forEach(player -> builder.suggest(player.getName().getString()));
        return EconomyManager.getInstance().getAllUsernames()
                .handle((names, error) -> {
                    if (error == null && names != null) {
                        names.forEach(builder::suggest);
                    }
                    return builder.build();
                });
    };

    private EcoAdminCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("eco")
                .then(Commands.literal("give")
                        .requires(source -> PermissionHelper.check(source, "economy_ld.admin.give", 2))
                        .then(Commands.argument("player", StringArgumentType.string())
                                .suggests(KNOWN_PLAYERS)
                                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                        .executes(ctx -> give(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "player"),
                                                LongArgumentType.getLong(ctx, "amount")
                                        )))))
                .then(Commands.literal("take")
                        .requires(source -> PermissionHelper.check(source, "economy_ld.admin.take", 2))
                        .then(Commands.argument("player", StringArgumentType.string())
                                .suggests(KNOWN_PLAYERS)
                                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                        .executes(ctx -> take(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "player"),
                                                LongArgumentType.getLong(ctx, "amount")
                                        )))))
                .then(Commands.literal("set")
                        .requires(source -> PermissionHelper.check(source, "economy_ld.admin.set", 2))
                        .then(Commands.argument("player", StringArgumentType.string())
                                .suggests(KNOWN_PLAYERS)
                                .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                        .executes(ctx -> set(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "player"),
                                                LongArgumentType.getLong(ctx, "amount")
                                        )))))
                .then(Commands.literal("balance")
                        .requires(source -> PermissionHelper.check(source, "economy_ld.admin.balance", 2))
                        .then(Commands.argument("player", StringArgumentType.string())
                                .suggests(KNOWN_PLAYERS)
                                .executes(ctx -> balance(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "player")
                                ))))
                .then(Commands.literal("adminmode")
                        .requires(source -> PermissionHelper.check(source, "economy_ld.admin.adminmode", 2))
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            boolean nowOn = EconomyManager.getInstance().toggleAdminMode(player.getUUID());
                            context.getSource().sendSuccess(() -> Component.literal(nowOn
                                    ? "Admin mode: ON - you can now manage admin shops."
                                    : "Admin mode: OFF - back to buyer view."), false);
                            return 1;
                        }))
                .then(Commands.literal("reload")
                        .requires(source -> PermissionHelper.check(source, "economy_ld.admin.reload", 2))
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
