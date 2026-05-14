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
                        .requires(source -> source.hasPermission(2))
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
            source.sendFailure(Component.literal("Only players can use /ah."));
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
            source.sendFailure(Component.literal("Only players can use /ah sell."));
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
            source.sendFailure(Component.literal("Only players can use /ah inbox."));
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
            source.sendFailure(Component.literal("Only players can use /ah list."));
            return 0;
        }

        EconomyManager.getInstance().getPlayerAuctions(player.getUUID(), source.getServer().registryAccess())
                .whenComplete((auctions, error) -> source.getServer().execute(() -> {
                    if (error != null) {
                        source.sendFailure(Component.literal("Failed to load your active listings."));
                        return;
                    }
                    if (auctions == null || auctions.isEmpty()) {
                        source.sendSuccess(() -> Component.literal("You have no active auction listings."), false);
                        return;
                    }
                    source.sendSuccess(() -> Component.literal("Your active listings:"), false);
                    long now = System.currentTimeMillis() / 1000L;
                    for (AuctionRecord auction : auctions) {
                        String shortId = auction.id().toString().substring(0, 8);
                        long price = auction.currentBid();
                        source.sendSuccess(() -> Component.literal(
                                "[" + shortId + "] "
                                        + auction.itemStack().getHoverName().getString()
                                        + " - current bid: " + CurrencyFormatter.format(price)
                                        + " - expires in " + formatDuration(Math.max(0L, auction.expiresAt() - now))
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
            source.sendFailure(Component.literal("Only players can use /ah bids."));
            return 0;
        }

        EconomyManager.getInstance().getPlayerBids(player.getUUID(), source.getServer().registryAccess())
                .whenComplete((auctions, error) -> source.getServer().execute(() -> {
                    if (error != null) {
                        source.sendFailure(Component.literal("Failed to load your current bids."));
                        return;
                    }
                    if (auctions == null || auctions.isEmpty()) {
                        source.sendSuccess(() -> Component.literal("You are not the highest bidder on any active auction."), false);
                        return;
                    }
                    source.sendSuccess(() -> Component.literal("Auctions where you are highest bidder:"), false);
                    long now = System.currentTimeMillis() / 1000L;
                    for (AuctionRecord auction : auctions) {
                        String shortId = auction.id().toString().substring(0, 8);
                        source.sendSuccess(() -> Component.literal(
                                "[" + shortId + "] "
                                        + auction.itemStack().getHoverName().getString()
                                        + " - your bid: " + CurrencyFormatter.format(auction.currentBid())
                                        + " - expires in " + formatDuration(Math.max(0L, auction.expiresAt() - now))
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
            source.sendFailure(Component.literal("Only players can use /ah cancel."));
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
                    if (!player.hasPermissions(2) && !match.sellerUuid().equals(player.getUUID())) {
                        return CompletableFuture.completedFuture(new CancelCommandResult(match, false, true));
                    }
                    return EconomyManager.getInstance().cancelAuction(match.id(), player.getUUID())
                            .thenApply(success -> new CancelCommandResult(match, Boolean.TRUE.equals(success), false));
                })
                .whenComplete((result, error) -> source.getServer().execute(() -> {
                    if (error != null || result == null) {
                        source.sendFailure(Component.literal("Failed to cancel auction: " + errorMessage(error)));
                        return;
                    }
                    if (result.forbidden()) {
                        source.sendFailure(Component.literal("You may only cancel your own listings."));
                        return;
                    }
                    if (result.auction() == null) {
                        source.sendFailure(Component.literal("No active auction found for prefix '" + idPrefixRaw + "'."));
                        return;
                    }
                    if (!result.success()) {
                        source.sendFailure(Component.literal("Auction cannot be cancelled (already bid on or already ended)."));
                        return;
                    }
                    source.sendSuccess(() -> Component.literal("Cancelled auction " + result.auction().id().toString().substring(0, 8) + "."), true);
                    AuctionNetworking.syncAuctionsToAll(source.getServer());
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
                        source.sendFailure(Component.literal("Failed to add bonus listing limit: " + errorMessage(error)));
                        return;
                    }
                    source.sendSuccess(() -> Component.literal(
                            "Updated " + result.player().username() + ": bonus=" + result.bonus()
                                    + ", effective limit=" + result.effectiveLimit()
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
                        source.sendFailure(Component.literal("Failed to remove bonus listing limit: " + errorMessage(error)));
                        return;
                    }
                    source.sendSuccess(() -> Component.literal(
                            "Updated " + result.player().username() + ": bonus=" + result.bonus()
                                    + ", effective limit=" + result.effectiveLimit()
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
                        source.sendFailure(Component.literal("Failed to set bonus listing limit: " + errorMessage(error)));
                        return;
                    }
                    source.sendSuccess(() -> Component.literal(
                            "Updated " + result.player().username() + ": bonus=" + result.bonus()
                                    + ", effective limit=" + result.effectiveLimit()
                    ), true);
                }));
        return 1;
    }

    private static int bonusGet(CommandSourceStack source, String playerName) {
        resolveKnownPlayer(source, playerName)
                .thenCompose(target -> EconomyManager.getInstance().getEffectiveListingLimit(
                                target.uuid(),
                                EconomyManager.getInstance().getAuctionConfig_defaultMaxListings())
                        .thenApply(effective -> new BonusResult(
                                target,
                                Math.max(0, effective - EconomyManager.getInstance().getAuctionConfig_defaultMaxListings()),
                                effective)))
                .whenComplete((result, error) -> source.getServer().execute(() -> {
                    if (error != null || result == null) {
                        source.sendFailure(Component.literal("Failed to get bonus listing limit: " + errorMessage(error)));
                        return;
                    }
                    source.sendSuccess(() -> Component.literal(
                            result.player().username() + ": bonus=" + result.bonus()
                                    + ", effective limit=" + result.effectiveLimit()
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
