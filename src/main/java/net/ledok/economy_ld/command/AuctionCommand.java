package net.ledok.economy_ld.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.ledok.economy_ld.auction.AuctionRecord;
import net.ledok.economy_ld.manager.EconomyManager;
import net.ledok.economy_ld.network.AuctionNetworking;
import net.ledok.economy_ld.network.packet.s2c.OpenAuctionScreenS2CPacket;
import net.ledok.economy_ld.network.packet.s2c.OpenInboxScreenS2CPacket;
import net.ledok.economy_ld.util.CurrencyFormatter;
import net.ledok.economy_ld.util.PermissionHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class AuctionCommand {
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

    private AuctionCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ah")
                .executes(ctx -> openAuctionScreen(ctx.getSource()))
                .then(Commands.literal("sell")
                        .then(Commands.argument("price", LongArgumentType.longArg(1))
                                .executes(ctx -> openAuctionSellFlow(ctx.getSource()))
                                .then(Commands.argument("buyout", LongArgumentType.longArg(1))
                                        .executes(ctx -> openAuctionSellFlow(ctx.getSource())))))
                .then(Commands.literal("list")
                        .executes(ctx -> listOwnAuctions(ctx.getSource())))
                .then(Commands.literal("bids")
                        .executes(ctx -> listOwnBids(ctx.getSource())))
                .then(Commands.literal("inbox")
                        .executes(ctx -> openInbox(ctx.getSource())))
                .then(Commands.literal("cancel")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> cancelAuctionByPrefix(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id")
                                ))))
                .then(Commands.literal("bonus_listing_limit")
                        .requires(source -> PermissionHelper.check(source, "economy_ld.admin.auction_limit", 2))
                        .then(Commands.argument("player", StringArgumentType.string())
                                .suggests(KNOWN_PLAYERS)
                                .then(Commands.literal("add")
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(ctx -> bonusAdd(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "amount")
                                                ))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(ctx -> bonusRemove(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "amount")
                                                ))))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                .executes(ctx -> bonusSet(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "amount")
                                                ))))
                                .then(Commands.literal("get")
                                        .executes(ctx -> bonusGet(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "player")
                                        ))))));
    }

    private static int openAuctionScreen(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.translatable("economy_ld.command.ah.players_only"));
            return 0;
        }

        ServerPlayNetworking.send(player, new OpenAuctionScreenS2CPacket());
        AuctionNetworking.syncAuctionsToPlayer(player);
        return 1;
    }

    private static int openAuctionSellFlow(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.translatable("economy_ld.command.ah.sell.players_only"));
            return 0;
        }
        ServerPlayNetworking.send(player, new OpenAuctionScreenS2CPacket());
        AuctionNetworking.syncAuctionsToPlayer(player);
        return 1;
    }

    private static int openInbox(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.translatable("economy_ld.command.ah.inbox.players_only"));
            return 0;
        }

        AuctionNetworking.syncInboxToPlayer(player);
        ServerPlayNetworking.send(player, new OpenInboxScreenS2CPacket());
        return 1;
    }

    private static int listOwnAuctions(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.translatable("economy_ld.command.ah.list.players_only"));
            return 0;
        }

        EconomyManager.getInstance().getPlayerAuctions(player.getUUID(), source.getServer().registryAccess())
                .whenComplete((auctions, error) -> source.getServer().execute(() -> {
                    if (error != null) {
                        source.sendFailure(Component.translatable("economy_ld.command.ah.list.error"));
                        return;
                    }
                    if (auctions == null || auctions.isEmpty()) {
                        source.sendSuccess(() -> Component.translatable("economy_ld.command.ah.list.empty"), false);
                        return;
                    }
                    source.sendSuccess(() -> Component.translatable("economy_ld.command.ah.list.header"), false);
                    long now = System.currentTimeMillis() / 1000L;
                    for (AuctionRecord auction : auctions) {
                        String shortId = auction.id().toString().substring(0, 8);
                        long price = auction.currentBid();
                        source.sendSuccess(() -> Component.translatable(
                                "economy_ld.command.ah.list.row",
                                shortId,
                                auction.itemStack().getHoverName().getString(),
                                CurrencyFormatter.format(price),
                                formatDuration(Math.max(0L, auction.expiresAt() - now))
                        ), false);
                    }
                }));
        return 1;
    }

    private static int listOwnBids(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.translatable("economy_ld.command.ah.bids.players_only"));
            return 0;
        }

        EconomyManager.getInstance().getPlayerBids(player.getUUID(), source.getServer().registryAccess())
                .whenComplete((auctions, error) -> source.getServer().execute(() -> {
                    if (error != null) {
                        source.sendFailure(Component.translatable("economy_ld.command.ah.bids.error"));
                        return;
                    }
                    if (auctions == null || auctions.isEmpty()) {
                        source.sendSuccess(() -> Component.translatable("economy_ld.command.ah.bids.empty"), false);
                        return;
                    }
                    source.sendSuccess(() -> Component.translatable("economy_ld.command.ah.bids.header"), false);
                    long now = System.currentTimeMillis() / 1000L;
                    for (AuctionRecord auction : auctions) {
                        String shortId = auction.id().toString().substring(0, 8);
                        source.sendSuccess(() -> Component.translatable(
                                "economy_ld.command.ah.bids.row",
                                shortId,
                                auction.itemStack().getHoverName().getString(),
                                CurrencyFormatter.format(auction.currentBid()),
                                formatDuration(Math.max(0L, auction.expiresAt() - now))
                        ), false);
                    }
                }));
        return 1;
    }

    private static int cancelAuctionByPrefix(CommandSourceStack source, String idPrefixRaw) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.translatable("economy_ld.command.ah.cancel.players_only"));
            return 0;
        }
        String idPrefix = idPrefixRaw.toLowerCase(Locale.ROOT);
        EconomyManager.getInstance().getActiveAuctions(source.getServer().registryAccess())
                .thenCompose(auctions -> {
                    AuctionRecord match = auctions.stream()
                            .filter(a -> a.id().toString().startsWith(idPrefix))
                            .findFirst()
                            .orElse(null);
                    if (match == null) {
                        return CompletableFuture.completedFuture(new CancelCommandResult(null, false, false));
                    }
                    if (!PermissionHelper.check(player, "economy_ld.admin.auction_cancel", 2) && !match.sellerUuid().equals(player.getUUID())) {
                        return CompletableFuture.completedFuture(new CancelCommandResult(match, false, true));
                    }
                    return EconomyManager.getInstance().cancelAuction(match.id(), player.getUUID())
                            .thenApply(success -> new CancelCommandResult(match, Boolean.TRUE.equals(success), false));
                })
                .whenComplete((result, error) -> source.getServer().execute(() -> {
                    if (error != null || result == null) {
                        source.sendFailure(Component.translatable("economy_ld.command.ah.cancel.error", errorMessage(error)));
                        return;
                    }
                    if (result.forbidden()) {
                        source.sendFailure(Component.translatable("economy_ld.command.ah.cancel.own_only"));
                        return;
                    }
                    if (result.auction() == null) {
                        source.sendFailure(Component.translatable("economy_ld.command.ah.cancel.not_found", idPrefixRaw));
                        return;
                    }
                    if (!result.success()) {
                        source.sendFailure(Component.translatable("economy_ld.command.ah.cancel.already_bid"));
                        return;
                    }
                    source.sendSuccess(() -> Component.translatable(
                            "economy_ld.command.ah.cancel.success",
                            result.auction().id().toString().substring(0, 8)
                    ), true);
                    AuctionNetworking.syncAuctionsToAll(source.getServer());
                    AuctionNetworking.syncInboxToPlayer(player);
                }));
        return 1;
    }

    private static int bonusAdd(CommandSourceStack source, String playerName, int amount) {
        resolveKnownPlayer(source, playerName)
                .thenCompose(target -> EconomyManager.getInstance().adjustAuctionBonusLimit(target.uuid(), Math.max(1, amount))
                        .thenCompose(newBonus -> EconomyManager.getInstance()
                                .getEffectiveListingLimit(target.uuid(), EconomyManager.getInstance().getAuctionConfig_defaultMaxListings())
                                .thenApply(effective -> new BonusResult(target, newBonus, effective))))
                .whenComplete((result, error) -> source.getServer().execute(() -> {
                    if (error != null || result == null) {
                        source.sendFailure(Component.translatable("economy_ld.command.ah.bonus.add.error", errorMessage(error)));
                        return;
                    }
                    source.sendSuccess(() -> Component.translatable(
                            "economy_ld.command.ah.bonus.add.success",
                            result.player().username(),
                            result.bonus(),
                            result.effectiveLimit()
                    ), true);
                }));
        return 1;
    }

    private static int bonusRemove(CommandSourceStack source, String playerName, int amount) {
        resolveKnownPlayer(source, playerName)
                .thenCompose(target -> EconomyManager.getInstance().adjustAuctionBonusLimit(target.uuid(), -Math.max(1, amount))
                        .thenCompose(newBonus -> EconomyManager.getInstance()
                                .getEffectiveListingLimit(target.uuid(), EconomyManager.getInstance().getAuctionConfig_defaultMaxListings())
                                .thenApply(effective -> new BonusResult(target, newBonus, effective))))
                .whenComplete((result, error) -> source.getServer().execute(() -> {
                    if (error != null || result == null) {
                        source.sendFailure(Component.translatable("economy_ld.command.ah.bonus.remove.error", errorMessage(error)));
                        return;
                    }
                    source.sendSuccess(() -> Component.translatable(
                            "economy_ld.command.ah.bonus.remove.success",
                            result.player().username(),
                            result.bonus(),
                            result.effectiveLimit()
                    ), true);
                }));
        return 1;
    }

    private static int bonusSet(CommandSourceStack source, String playerName, int amount) {
        resolveKnownPlayer(source, playerName)
                .thenCompose(target -> EconomyManager.getInstance().setAuctionBonusLimit(target.uuid(), amount)
                        .thenCompose(v -> EconomyManager.getInstance().getEffectiveListingLimit(
                                target.uuid(), EconomyManager.getInstance().getAuctionConfig_defaultMaxListings())
                                .thenApply(effective -> new BonusResult(target, Math.max(0, amount), effective))))
                .whenComplete((result, error) -> source.getServer().execute(() -> {
                    if (error != null || result == null) {
                        source.sendFailure(Component.translatable("economy_ld.command.ah.bonus.set.error", errorMessage(error)));
                        return;
                    }
                    source.sendSuccess(() -> Component.translatable(
                            "economy_ld.command.ah.bonus.set.success",
                            result.player().username(),
                            result.bonus(),
                            result.effectiveLimit()
                    ), true);
                }));
        return 1;
    }

    private static int bonusGet(CommandSourceStack source, String playerName) {
        resolveKnownPlayer(source, playerName)
                .thenCompose(target -> {
                    int defaultLimit = EconomyManager.getInstance().getAuctionConfig_defaultMaxListings();
                    return EconomyManager.getInstance().getAuctionBonusLimit(target.uuid())
                            .thenCombine(
                                    EconomyManager.getInstance().getEffectiveListingLimit(target.uuid(), defaultLimit),
                                    (bonus, effective) -> new BonusResult(target, bonus, effective)
                            );
                })
                .whenComplete((result, error) -> source.getServer().execute(() -> {
                    if (error != null || result == null) {
                        source.sendFailure(Component.translatable("economy_ld.command.ah.bonus.get.error", errorMessage(error)));
                        return;
                    }
                    source.sendSuccess(() -> Component.translatable(
                            "economy_ld.command.ah.bonus.get.success",
                            result.player().username(),
                            result.bonus(),
                            result.effectiveLimit(),
                            EconomyManager.getInstance().getAuctionConfig_defaultMaxListings()
                    ), false);
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

    private static String formatDuration(long seconds) {
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return Math.max(0L, minutes) + "m";
    }

    private static String errorMessage(Throwable error) {
        if (error instanceof CompletionException && error.getCause() != null) {
            return error.getCause().getMessage();
        }
        return error == null ? "unknown error" : error.getMessage();
    }

    private record ResolvedPlayer(UUID uuid, String username) {
    }

    private record CancelCommandResult(AuctionRecord auction, boolean success, boolean forbidden) {
    }

    private record BonusResult(ResolvedPlayer player, int bonus, int effectiveLimit) {
    }
}
