package net.ledok.economy_ld.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.ledok.economy_ld.EconomyLdMod;
import net.ledok.economy_ld.auction.AuctionRecord;
import net.ledok.economy_ld.auction.PendingDelivery;
import net.ledok.economy_ld.client.screen.AuctionClientState;
import net.ledok.economy_ld.client.screen.InboxClientState;
import net.ledok.economy_ld.manager.EconomyManager;
import net.ledok.economy_ld.network.packet.c2s.BuyoutAuctionC2SPacket;
import net.ledok.economy_ld.network.packet.c2s.CancelAuctionC2SPacket;
import net.ledok.economy_ld.network.packet.c2s.ClaimAllInboxC2SPacket;
import net.ledok.economy_ld.network.packet.c2s.ClaimInboxItemC2SPacket;
import net.ledok.economy_ld.network.packet.c2s.PlaceAuctionC2SPacket;
import net.ledok.economy_ld.network.packet.c2s.PlaceBidC2SPacket;
import net.ledok.economy_ld.network.packet.s2c.AuctionActionResultS2CPacket;
import net.ledok.economy_ld.network.packet.s2c.AuctionListSyncS2CPacket;
import net.ledok.economy_ld.network.packet.s2c.InboxSyncS2CPacket;
import net.ledok.economy_ld.network.packet.s2c.OpenAuctionScreenS2CPacket;
import net.ledok.economy_ld.network.packet.s2c.OpenInboxScreenS2CPacket;
import net.ledok.economy_ld.util.ItemStackSerializationUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class AuctionNetworking {
    private AuctionNetworking() {
    }

    public static void registerPayloadTypes() {
        PayloadTypeRegistry.playC2S().register(PlaceAuctionC2SPacket.TYPE, PlaceAuctionC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(PlaceBidC2SPacket.TYPE, PlaceBidC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(BuyoutAuctionC2SPacket.TYPE, BuyoutAuctionC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(CancelAuctionC2SPacket.TYPE, CancelAuctionC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(ClaimInboxItemC2SPacket.TYPE, ClaimInboxItemC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(ClaimAllInboxC2SPacket.TYPE, ClaimAllInboxC2SPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(AuctionListSyncS2CPacket.TYPE, AuctionListSyncS2CPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(AuctionActionResultS2CPacket.TYPE, AuctionActionResultS2CPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenAuctionScreenS2CPacket.TYPE, OpenAuctionScreenS2CPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(InboxSyncS2CPacket.TYPE, InboxSyncS2CPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenInboxScreenS2CPacket.TYPE, OpenInboxScreenS2CPacket.CODEC);
    }

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(PlaceAuctionC2SPacket.TYPE, (payload, context) ->
                context.server().execute(() -> handlePlaceAuction(payload, context.player())));

        ServerPlayNetworking.registerGlobalReceiver(PlaceBidC2SPacket.TYPE, (payload, context) ->
                context.server().execute(() -> handlePlaceBid(payload, context.player())));

        ServerPlayNetworking.registerGlobalReceiver(BuyoutAuctionC2SPacket.TYPE, (payload, context) ->
                context.server().execute(() -> handleBuyout(payload, context.player())));

        ServerPlayNetworking.registerGlobalReceiver(CancelAuctionC2SPacket.TYPE, (payload, context) ->
                context.server().execute(() -> handleCancel(payload, context.player())));

        ServerPlayNetworking.registerGlobalReceiver(ClaimInboxItemC2SPacket.TYPE, (payload, context) ->
                context.server().execute(() -> handleClaimInboxItem(payload, context.player())));

        ServerPlayNetworking.registerGlobalReceiver(ClaimAllInboxC2SPacket.TYPE, (payload, context) ->
                context.server().execute(() -> handleClaimAllInbox(context.player())));
    }

    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(AuctionListSyncS2CPacket.TYPE, (payload, context) -> {
            AuctionClientState.setAuctions(payload.auctions());
            AuctionClientState.setPlayerBalance(payload.playerBalance());
        });
        ClientPlayNetworking.registerGlobalReceiver(AuctionActionResultS2CPacket.TYPE, (payload, context) ->
                AuctionClientState.setLastResult(payload));
        ClientPlayNetworking.registerGlobalReceiver(OpenAuctionScreenS2CPacket.TYPE, (payload, context) ->
                AuctionClientState.markOpenRequested());
        ClientPlayNetworking.registerGlobalReceiver(InboxSyncS2CPacket.TYPE, (payload, context) ->
                InboxClientState.setDeliveries(payload.deliveries()));
        ClientPlayNetworking.registerGlobalReceiver(OpenInboxScreenS2CPacket.TYPE, (payload, context) ->
                InboxClientState.markOpenRequested());
    }

    public static void syncAuctionsToPlayer(ServerPlayer player) {
        EconomyManager manager = EconomyManager.getInstance();
        manager.getActiveAuctions(player.server.registryAccess())
                .thenAccept(auctions -> manager.getBalance(player.getUUID(), player.getName().getString())
                        .thenAccept(balance -> player.server.execute(() ->
                                ServerPlayNetworking.send(player, new AuctionListSyncS2CPacket(auctions, balance)))));
    }

    public static void syncAuctionsToAll(MinecraftServer server) {
        EconomyManager manager = EconomyManager.getInstance();
        manager.getActiveAuctions(server.registryAccess())
                .thenAccept(auctions -> server.execute(() ->
                        server.getPlayerList().getPlayers().forEach(player ->
                                manager.getBalance(player.getUUID(), player.getName().getString())
                                        .thenAccept(balance -> server.execute(() ->
                                                ServerPlayNetworking.send(player, new AuctionListSyncS2CPacket(auctions, balance)))))));
    }

    public static void syncInboxToPlayer(ServerPlayer player) {
        EconomyManager.getInstance().getPendingDeliveries(player.getUUID())
                .thenAccept(deliveries -> player.server.execute(() ->
                        ServerPlayNetworking.send(player, new InboxSyncS2CPacket(deliveries))));
    }

    private static void handlePlaceAuction(PlaceAuctionC2SPacket payload, ServerPlayer player) {
        EconomyManager manager = EconomyManager.getInstance();
        ItemStack item = ItemStack.parseOptional(player.registryAccess(), payload.itemNbt());
        if (item.isEmpty()) {
            sendActionResult(player, new AuctionActionResultS2CPacket(
                    AuctionActionResultS2CPacket.ActionType.ALREADY_ENDED,
                    "Unknown Item",
                    0L
            ));
            return;
        }

        int quantity = Math.max(1, payload.quantity());
        int available = countMatchingItems(player, item);
        if (available < quantity) {
            sendActionResult(player, new AuctionActionResultS2CPacket(
                    AuctionActionResultS2CPacket.ActionType.NOT_ENOUGH_ITEMS,
                    item.getHoverName().getString(),
                    0L
            ));
            return;
        }

        long startPrice = Math.max(1L, payload.startPrice());
        long durationSeconds = Math.max(60L, payload.durationSeconds());
        long expiresAt = (System.currentTimeMillis() / 1000L) + durationSeconds;
        Long buyoutPrice = normalizeBuyout(payload.buyoutPrice(), startPrice, manager.getAuctionConfig_buyoutEnabled());
        long listingFee = (startPrice * manager.getAuctionConfig_listingFeePercent()) / 100L;
        String itemName = item.getHoverName().getString();

        manager.getEffectiveListingLimit(player.getUUID(), manager.getAuctionConfig_defaultMaxListings())
                .thenCompose(limit -> manager.placeAuction(
                        player.getUUID(),
                        player.getName().getString(),
                        item.copyWithCount(1),
                        quantity,
                        startPrice,
                        buyoutPrice,
                        expiresAt,
                        manager.getAuctionConfig_listingFeePercent(),
                        limit,
                        player.registryAccess()
                ).thenCompose(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        return java.util.concurrent.CompletableFuture.completedFuture(new PlaceAuctionResult(true, false, limit));
                    }
                    return manager.getPlayerAuctions(player.getUUID(), player.server.registryAccess())
                            .thenApply(ownAuctions -> new PlaceAuctionResult(false, ownAuctions.size() >= limit, limit));
                }))
                .whenComplete((result, error) -> player.server.execute(() -> {
                    if (error != null || result == null) {
                        EconomyLdMod.LOGGER.warn("Failed to place auction for {}", player.getName().getString(), error);
                        return;
                    }
                    if (!result.success()) {
                        sendActionResult(player, new AuctionActionResultS2CPacket(
                                result.limitReached()
                                        ? AuctionActionResultS2CPacket.ActionType.LIMIT_REACHED
                                        : AuctionActionResultS2CPacket.ActionType.INSUFFICIENT_FUNDS,
                                itemName,
                                result.limitReached() ? result.limit() : listingFee
                        ));
                        return;
                    }

                    int removed = removeMatchingItems(player, item, quantity);
                    if (removed < quantity) {
                        if (removed > 0) {
                            player.getInventory().add(item.copyWithCount(removed));
                        }
                        EconomyLdMod.LOGGER.warn("Player {} placed auction but inventory removal failed ({}/{})",
                                player.getName().getString(), removed, quantity);
                    }
                    player.inventoryMenu.sendAllDataToRemote();
                    sendActionResult(player, new AuctionActionResultS2CPacket(
                            AuctionActionResultS2CPacket.ActionType.LISTED,
                            itemName,
                            listingFee
                    ));
                    syncAuctionsToAll(player.server);
                }));
    }

    private static void handlePlaceBid(PlaceBidC2SPacket payload, ServerPlayer player) {
        EconomyManager manager = EconomyManager.getInstance();
        long bidAmount = Math.max(1L, payload.bidAmount());

        manager.getActiveAuctions(player.server.registryAccess())
                .thenCompose(auctions -> {
                    AuctionRecord current = auctions.stream()
                            .filter(auction -> auction.id().equals(payload.auctionId()))
                            .findFirst()
                            .orElse(null);

                    return manager.placeBid(payload.auctionId(), player.getUUID(), player.getName().getString(), bidAmount)
                            .thenApply(success -> new BidResult(current, Boolean.TRUE.equals(success)));
                })
                .whenComplete((result, error) -> player.server.execute(() -> {
                    if (error != null || result == null) {
                        EconomyLdMod.LOGGER.warn("Failed to place bid {}", payload.auctionId(), error);
                        return;
                    }

                    String itemName = result.current() == null ? "Auction Item" : result.current().itemStack().getHoverName().getString();
                    if (!result.success()) {
                        manager.getBalance(player.getUUID(), player.getName().getString())
                                .whenComplete((balance, balanceError) -> player.server.execute(() -> {
                                    long currentBalance = balanceError == null && balance != null ? balance : 0L;
                                    AuctionActionResultS2CPacket.ActionType type = currentBalance < bidAmount
                                            ? AuctionActionResultS2CPacket.ActionType.INSUFFICIENT_FUNDS
                                            : AuctionActionResultS2CPacket.ActionType.ALREADY_ENDED;
                                    sendActionResult(player, new AuctionActionResultS2CPacket(type, itemName, bidAmount));
                                }));
                        return;
                    }

                    sendActionResult(player, new AuctionActionResultS2CPacket(
                            AuctionActionResultS2CPacket.ActionType.BID_PLACED,
                            itemName,
                            bidAmount
                    ));

                    if (result.current() != null && result.current().bidderUuid() != null
                            && !result.current().bidderUuid().equals(player.getUUID())) {
                        ServerPlayer outbid = player.server.getPlayerList().getPlayer(result.current().bidderUuid());
                        if (outbid != null) {
                            sendActionResult(outbid, new AuctionActionResultS2CPacket(
                                    AuctionActionResultS2CPacket.ActionType.OUTBID,
                                    itemName,
                                    bidAmount
                            ));
                        }
                    }

                    syncAuctionsToAll(player.server);
                }));
    }

    private static void handleBuyout(BuyoutAuctionC2SPacket payload, ServerPlayer player) {
        EconomyManager manager = EconomyManager.getInstance();
        manager.getActiveAuctions(player.server.registryAccess())
                .thenCompose(auctions -> {
                    AuctionRecord current = auctions.stream()
                            .filter(auction -> auction.id().equals(payload.auctionId()))
                            .findFirst()
                            .orElse(null);
                    return manager.buyout(
                            payload.auctionId(),
                            player.getUUID(),
                            player.getName().getString(),
                            manager.getAuctionConfig_serverTaxPercent()
                    ).thenApply(success -> new BuyoutResult(current, Boolean.TRUE.equals(success)));
                })
                .whenComplete((result, error) -> player.server.execute(() -> {
                    if (error != null || result == null) {
                        EconomyLdMod.LOGGER.warn("Failed to buyout auction {}", payload.auctionId(), error);
                        return;
                    }

                    String itemName = result.current() == null ? "Auction Item" : result.current().itemStack().getHoverName().getString();
                    long buyoutPrice = result.current() == null || result.current().buyoutPrice() == null
                            ? 0L
                            : result.current().buyoutPrice();
                    if (!result.success()) {
                        manager.getBalance(player.getUUID(), player.getName().getString())
                                .whenComplete((balance, balanceError) -> player.server.execute(() -> {
                                    long currentBalance = balanceError == null && balance != null ? balance : 0L;
                                    AuctionActionResultS2CPacket.ActionType type = currentBalance < buyoutPrice
                                            ? AuctionActionResultS2CPacket.ActionType.INSUFFICIENT_FUNDS
                                            : AuctionActionResultS2CPacket.ActionType.ALREADY_ENDED;
                                    sendActionResult(player, new AuctionActionResultS2CPacket(type, itemName, buyoutPrice));
                                }));
                        return;
                    }

                    sendActionResult(player, new AuctionActionResultS2CPacket(
                            AuctionActionResultS2CPacket.ActionType.BUYOUT,
                            itemName,
                            buyoutPrice
                    ));
                    syncAuctionsToAll(player.server);
                    notifyInboxUpdate(player);

                    if (result.current() != null && result.current().sellerUuid() != null) {
                        ServerPlayer sellerOnline = player.server.getPlayerList().getPlayer(result.current().sellerUuid());
                        if (sellerOnline != null && !sellerOnline.getUUID().equals(player.getUUID())) {
                            notifyInboxUpdate(sellerOnline);
                        }
                    }
                }));
    }

    private static void handleCancel(CancelAuctionC2SPacket payload, ServerPlayer player) {
        EconomyManager manager = EconomyManager.getInstance();
        manager.getActiveAuctions(player.server.registryAccess())
                .thenCompose(auctions -> {
                    AuctionRecord current = auctions.stream()
                            .filter(auction -> auction.id().equals(payload.auctionId()))
                            .findFirst()
                            .orElse(null);

                    if (current == null) {
                        return java.util.concurrent.CompletableFuture.completedFuture(new CancelResult(null, false, false));
                    }
                    boolean allowed = player.hasPermissions(2) || current.sellerUuid().equals(player.getUUID());
                    if (!allowed) {
                        return java.util.concurrent.CompletableFuture.completedFuture(new CancelResult(current, false, true));
                    }
                    return manager.cancelAuction(payload.auctionId(), player.getUUID())
                            .thenApply(success -> new CancelResult(current, Boolean.TRUE.equals(success), false));
                })
                .whenComplete((result, error) -> player.server.execute(() -> {
                    if (error != null || result == null) {
                        EconomyLdMod.LOGGER.warn("Failed to cancel auction {}", payload.auctionId(), error);
                        return;
                    }
                    if (result.forbidden()) {
                        sendActionResult(player, new AuctionActionResultS2CPacket(
                                AuctionActionResultS2CPacket.ActionType.ALREADY_ENDED,
                                "Auction Item",
                                0L
                        ));
                        return;
                    }
                    if (!result.success()) {
                        sendActionResult(player, new AuctionActionResultS2CPacket(
                                AuctionActionResultS2CPacket.ActionType.ALREADY_ENDED,
                                result.current() == null ? "Auction Item" : result.current().itemStack().getHoverName().getString(),
                                0L
                        ));
                        return;
                    }

                    sendActionResult(player, new AuctionActionResultS2CPacket(
                            AuctionActionResultS2CPacket.ActionType.CANCELLED,
                            result.current().itemStack().getHoverName().getString(),
                            0L
                    ));
                    syncAuctionsToAll(player.server);
                    syncInboxToPlayer(player);
                }));
    }

    private static void handleClaimInboxItem(ClaimInboxItemC2SPacket payload, ServerPlayer player) {
        EconomyManager manager = EconomyManager.getInstance();
        manager.getPendingDeliveries(player.getUUID())
                .thenCompose(deliveries -> {
                    boolean belongsToPlayer = deliveries.stream().anyMatch(d -> d.id() == payload.deliveryId());
                    if (!belongsToPlayer) {
                        return java.util.concurrent.CompletableFuture.completedFuture(Optional.<PendingDelivery>empty());
                    }
                    return manager.claimSingleDelivery(payload.deliveryId());
                })
                .whenComplete((deliveryOpt, error) -> player.server.execute(() -> {
                    if (error != null) {
                        EconomyLdMod.LOGGER.warn("Failed to claim inbox item {} for {}", payload.deliveryId(),
                                player.getName().getString(), error);
                        syncInboxToPlayer(player);
                        return;
                    }
                    deliveryOpt.ifPresent(delivery -> deliverPending(player, List.of(delivery), manager));
                    syncInboxToPlayer(player);
                }));
    }

    private static void handleClaimAllInbox(ServerPlayer player) {
        EconomyManager manager = EconomyManager.getInstance();
        manager.claimAllDeliveries(player.getUUID())
                .whenComplete((deliveries, error) -> player.server.execute(() -> {
                    if (error != null) {
                        EconomyLdMod.LOGGER.warn("Failed to claim all inbox items for {}", player.getName().getString(), error);
                        syncInboxToPlayer(player);
                        return;
                    }
                    deliverPending(player, deliveries, manager);
                    syncInboxToPlayer(player);
                }));
    }

    private static void sendActionResult(ServerPlayer player, AuctionActionResultS2CPacket packet) {
        ServerPlayNetworking.send(player, packet);
    }

    private static void notifyInboxUpdate(ServerPlayer player) {
        syncInboxToPlayer(player);
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "📦 Your item has been sent to your auction inbox! Use /ah inbox to claim it."
        ));
        sendActionResult(player, new AuctionActionResultS2CPacket(
                AuctionActionResultS2CPacket.ActionType.ITEM_SENT_TO_INBOX,
                "",
                0L
        ));
    }

    private static Long normalizeBuyout(Long rawBuyout, long startPrice, boolean buyoutEnabled) {
        if (!buyoutEnabled || rawBuyout == null || rawBuyout <= 0L) {
            return null;
        }
        return Math.max(startPrice, rawBuyout);
    }

    private static int countMatchingItems(ServerPlayer player, ItemStack template) {
        ItemStack normalizedTemplate;
        try {
            String encoded = ItemStackSerializationUtil.toBase64(template, player.registryAccess());
            normalizedTemplate = ItemStackSerializationUtil.fromBase64(encoded, player.registryAccess());
        } catch (Exception e) {
            normalizedTemplate = template;
        }

        int available = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack normalizedStack;
            try {
                String encoded = ItemStackSerializationUtil.toBase64(stack.copyWithCount(1), player.registryAccess());
                normalizedStack = ItemStackSerializationUtil.fromBase64(encoded, player.registryAccess());
            } catch (Exception e) {
                continue;
            }
            if (!ItemStack.isSameItemSameComponents(normalizedStack, normalizedTemplate)) {
                continue;
            }
            available += stack.getCount();
        }
        return available;
    }

    private static int removeMatchingItems(ServerPlayer player, ItemStack template, int wanted) {
        ItemStack normalizedTemplate;
        try {
            String encoded = ItemStackSerializationUtil.toBase64(template, player.registryAccess());
            normalizedTemplate = ItemStackSerializationUtil.fromBase64(encoded, player.registryAccess());
        } catch (Exception e) {
            normalizedTemplate = template;
        }

        int removed = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack normalizedStack;
            try {
                String encoded = ItemStackSerializationUtil.toBase64(stack.copyWithCount(1), player.registryAccess());
                normalizedStack = ItemStackSerializationUtil.fromBase64(encoded, player.registryAccess());
            } catch (Exception e) {
                continue;
            }
            if (!ItemStack.isSameItemSameComponents(normalizedStack, normalizedTemplate)) {
                continue;
            }
            int take = Math.min(wanted - removed, stack.getCount());
            stack.shrink(take);
            removed += take;
            if (removed >= wanted) {
                break;
            }
        }
        return removed;
    }

    private static void deliverPending(ServerPlayer player, List<PendingDelivery> deliveries, EconomyManager manager) {
        if (deliveries == null || deliveries.isEmpty()) {
            return;
        }

        for (PendingDelivery delivery : deliveries) {
            if (delivery.itemStack() != null && !delivery.itemStack().isEmpty()) {
                ItemStack give = delivery.itemStack().copyWithCount(Math.max(1, delivery.quantity()));
                if (!player.getInventory().add(give)) {
                    player.drop(give, false);
                }
                continue;
            }
            if (delivery.lcAmount() != null && delivery.lcAmount() > 0L) {
                manager.give(player.getUUID(), player.getName().getString(), delivery.lcAmount());
            }
        }

        player.inventoryMenu.sendAllDataToRemote();
    }

    private record PlaceAuctionResult(boolean success, boolean limitReached, int limit) {
    }

    private record BidResult(AuctionRecord current, boolean success) {
    }

    private record BuyoutResult(AuctionRecord current, boolean success) {
    }

    private record CancelResult(AuctionRecord current, boolean success, boolean forbidden) {
    }
}
