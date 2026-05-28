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
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                        .executes(ctx -> giveTargets(
                                                ctx.getSource(),
                                                EntityArgument.getPlayers(ctx, "targets"),
                                                LongArgumentType.getLong(ctx, "amount")
                                        ))))
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
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                        .executes(ctx -> setTargets(
                                                ctx.getSource(),
                                                EntityArgument.getPlayers(ctx, "targets"),
                                                LongArgumentType.getLong(ctx, "amount")
                                        ))))
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
                            context.getSource().sendSuccess(() -> Component.translatable(nowOn
                                    ? "economy_ld.command.eco.adminmode.on"
                                    : "economy_ld.command.eco.adminmode.off"), false);
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
                            source.sendFailure(Component.translatable("economy_ld.command.eco.unknown_player", username));
                        } else {
                            source.sendFailure(Component.translatable("economy_ld.command.eco.give.error", errorMessage(error)));
                        }
                        return;
                    }
                    source.sendSuccess(() -> Component.translatable("economy_ld.command.eco.give.success", CurrencyFormatter.format(amount), username), true);
                }));
        return 1;
    }

    private static int giveTargets(CommandSourceStack source, Collection<ServerPlayer> targets, long amount) {
        EconomyManager manager = EconomyManager.getInstance();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (ServerPlayer target : targets) {
            futures.add(manager.give(target.getUUID(), target.getName().getString(), amount)
                    .whenComplete((ignored, error) -> source.getServer().execute(() -> {
                        if (error != null) {
                            source.sendFailure(Component.translatable("economy_ld.command.eco.give.error", errorMessage(error)));
                            return;
                        }
                        source.sendSuccess(() -> Component.translatable(
                                "economy_ld.command.eco.give.success",
                                CurrencyFormatter.format(amount),
                                target.getName().getString()
                        ), true);
                    })));
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        return targets.size();
    }

    private static int take(CommandSourceStack source, String username, long amount) {
        resolveKnownPlayer(source, username).thenCompose(target -> EconomyManager.getInstance().take(target.uuid(), target.username(), amount))
                .whenComplete((success, error) -> source.getServer().execute(() -> {
                    if (error != null) {
                        if (isUnknownPlayer(error)) {
                            source.sendFailure(Component.translatable("economy_ld.command.eco.unknown_player", username));
                        } else {
                            source.sendFailure(Component.translatable("economy_ld.command.eco.take.error", errorMessage(error)));
                        }
                        return;
                    }
                    if (!success) {
                        source.sendFailure(Component.translatable("economy_ld.command.eco.take.insufficient", username));
                        return;
                    }
                    source.sendSuccess(() -> Component.translatable("economy_ld.command.eco.take.success", CurrencyFormatter.format(amount), username), true);
                }));
        return 1;
    }

    private static int set(CommandSourceStack source, String username, long amount) {
        resolveKnownPlayer(source, username).thenCompose(target -> EconomyManager.getInstance().set(target.uuid(), target.username(), amount))
                .whenComplete((ignored, error) -> source.getServer().execute(() -> {
                    if (error != null) {
                        if (isUnknownPlayer(error)) {
                            source.sendFailure(Component.translatable("economy_ld.command.eco.unknown_player", username));
                        } else {
                            source.sendFailure(Component.translatable("economy_ld.command.eco.set.error", errorMessage(error)));
                        }
                        return;
                    }
                    source.sendSuccess(() -> Component.translatable("economy_ld.command.eco.set.success", username, CurrencyFormatter.format(amount)), true);
                }));
        return 1;
    }

    private static int setTargets(CommandSourceStack source, Collection<ServerPlayer> targets, long amount) {
        EconomyManager manager = EconomyManager.getInstance();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (ServerPlayer target : targets) {
            futures.add(manager.set(target.getUUID(), target.getName().getString(), amount)
                    .whenComplete((ignored, error) -> source.getServer().execute(() -> {
                        if (error != null) {
                            source.sendFailure(Component.translatable("economy_ld.command.eco.set.error", errorMessage(error)));
                            return;
                        }
                        source.sendSuccess(() -> Component.translatable(
                                "economy_ld.command.eco.set.success",
                                target.getName().getString(),
                                CurrencyFormatter.format(amount)
                        ), true);
                    })));
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        return targets.size();
    }

    private static int balance(CommandSourceStack source, String username) {
        EconomyManager.getInstance().getBalanceByUsername(username)
                .whenComplete((balanceOpt, error) -> source.getServer().execute(() -> {
                    if (error != null) {
                        source.sendFailure(Component.translatable("economy_ld.command.eco.balance.error", username));
                        return;
                    }
                    if (balanceOpt.isEmpty()) {
                        source.sendFailure(Component.translatable("economy_ld.command.eco.balance.no_wallet", username));
                        return;
                    }
                    source.sendSuccess(() -> Component.translatable(
                            "economy_ld.command.eco.balance.success",
                            username,
                            CurrencyFormatter.format(balanceOpt.getAsLong())
                    ), false);
                }));
        return 1;
    }

    private static int reload(CommandSourceStack source) {
        EconomyManager.getInstance().reloadConfig()
                .whenComplete((ignored, error) -> source.getServer().execute(() -> {
                    if (error != null) {
                        source.sendFailure(Component.translatable("economy_ld.command.eco.reload.error"));
                        return;
                    }
                    source.sendSuccess(() -> Component.translatable("economy_ld.command.eco.reload.success"), true);
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
