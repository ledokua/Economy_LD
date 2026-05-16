package net.ledok.economy_ld.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.ledok.economy_ld.EconomyLdMod;
import net.ledok.economy_ld.client.screen.ShopClientState;
import net.ledok.economy_ld.manager.EconomyManager;
import net.ledok.economy_ld.network.packet.c2s.AddListingC2SPacket;
import net.ledok.economy_ld.network.packet.c2s.BuyItemC2SPacket;
import net.ledok.economy_ld.network.packet.c2s.RemoveListingC2SPacket;
import net.ledok.economy_ld.network.packet.c2s.RestockListingC2SPacket;
import net.ledok.economy_ld.network.packet.c2s.SellItemC2SPacket;
import net.ledok.economy_ld.network.packet.c2s.UpdateListingC2SPacket;
import net.ledok.economy_ld.network.packet.s2c.ShopActionResultS2CPacket;
import net.ledok.economy_ld.network.packet.s2c.ShopListingsSyncS2CPacket;
import net.ledok.economy_ld.screen.ShopBrowseScreenHandler;
import net.ledok.economy_ld.util.ItemStackSerializationUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ShopNetworking {
    private ShopNetworking() {
    }

    public static void registerPayloadTypes() {
        PayloadTypeRegistry.playS2C().register(ShopListingsSyncS2CPacket.TYPE, ShopListingsSyncS2CPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(ShopActionResultS2CPacket.TYPE, ShopActionResultS2CPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(BuyItemC2SPacket.TYPE, BuyItemC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(SellItemC2SPacket.TYPE, SellItemC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(AddListingC2SPacket.TYPE, AddListingC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateListingC2SPacket.TYPE, UpdateListingC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(RemoveListingC2SPacket.TYPE, RemoveListingC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(RestockListingC2SPacket.TYPE, RestockListingC2SPacket.CODEC);
    }

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(BuyItemC2SPacket.TYPE, (payload, context) -> {
            ShopBrowseScreenHandler menu = activeMenu(context.player());
            if (menu == null) {
                return;
            }
            EconomyManager.getInstance().getListings(menu.getShopId()).whenComplete((listings, error) -> {
                if (error != null) {
                    return;
                }
                listings.stream().filter(l -> l.id().equals(payload.listingId())).findFirst().ifPresent(listing -> {
                    int quantity = Math.max(1, payload.quantity());
                    if (listing.stock() != null && listing.stock() < quantity) {
                        context.server().execute(() -> sendActionResult(context.player(), new ShopActionResultS2CPacket(
                                ShopActionResultS2CPacket.ActionType.OUT_OF_STOCK,
                                listing.itemStack().getHoverName().getString(),
                                quantity,
                                listing.priceBuy() == null ? 0L : listing.priceBuy(),
                                0L
                        )));
                        return;
                    }
                    EconomyManager.getInstance().buyItem(listing.id(), context.player().getUUID(), context.player().getName().getString(), quantity)
                            .whenComplete((success, err2) -> context.server().execute(() -> {
                                if (err2 != null) {
                                    return;
                                }
                                if (Boolean.TRUE.equals(success)) {
                                    ItemStack toGive = listing.itemStack().copyWithCount(quantity);
                                    boolean added = context.player().getInventory().add(toGive);
                                    if (!added && !toGive.isEmpty()) {
                                        context.player().drop(toGive, false);
                                    }
                                    sendActionResult(context.player(), new ShopActionResultS2CPacket(
                                            ShopActionResultS2CPacket.ActionType.BOUGHT,
                                            listing.itemStack().getHoverName().getString(),
                                            quantity,
                                            listing.priceBuy() == null ? 0L : listing.priceBuy(),
                                            0L
                                    ));
                                    syncShop(context.player(), menu.getShopId(), menu.isAdminShop(), menu.isOwnerOrOperator());
                                    return;
                                }

                                EconomyManager.getInstance().getBalance(context.player().getUUID(), context.player().getName().getString())
                                        .whenComplete((balance, balanceError) -> context.server().execute(() -> {
                                            long currentBalance = balanceError == null && balance != null ? balance : 0L;
                                            ShopActionResultS2CPacket.ActionType type = (listing.stock() != null && listing.stock() < quantity)
                                                    ? ShopActionResultS2CPacket.ActionType.OUT_OF_STOCK
                                                    : ShopActionResultS2CPacket.ActionType.INSUFFICIENT_FUNDS;
                                            sendActionResult(context.player(), new ShopActionResultS2CPacket(
                                                    type,
                                                    listing.itemStack().getHoverName().getString(),
                                                    quantity,
                                                    listing.priceBuy() == null ? 0L : listing.priceBuy(),
                                                    currentBalance
                                            ));
                                            syncShop(context.player(), menu.getShopId(), menu.isAdminShop(), menu.isOwnerOrOperator());
                                        }));
                            }));
                });
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SellItemC2SPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ShopBrowseScreenHandler menu = activeMenu(context.player());
                if (menu == null) {
                    return;
                }
                EconomyManager.getInstance().getListings(menu.getShopId()).whenComplete((listings, error) ->
                        context.server().execute(() -> {
                            if (error != null) {
                                return;
                            }
                            listings.stream().filter(l -> l.id().equals(payload.listingId())).findFirst().ifPresent(listing -> {
                                int quantity = Math.max(1, payload.quantity());
                                int available = countMatchingItems(context.player(), listing.itemStack());
                                if (available < quantity) {
                                    sendActionResult(context.player(), new ShopActionResultS2CPacket(
                                            ShopActionResultS2CPacket.ActionType.SHOP_FULL,
                                            listing.itemStack().getHoverName().getString(),
                                            quantity,
                                            0L,
                                            0L
                                    ));
                                    return;
                                }

                                EconomyManager.getInstance().sellItem(listing.id(), context.player().getUUID(), context.player().getName().getString(), quantity)
                                        .whenComplete((success, err2) -> context.server().execute(() -> {
                                            if (err2 != null || !Boolean.TRUE.equals(success)) {
                                                sendActionResult(context.player(), new ShopActionResultS2CPacket(
                                                        ShopActionResultS2CPacket.ActionType.SHOP_FULL,
                                                        listing.itemStack().getHoverName().getString(),
                                                        quantity,
                                                        0L,
                                                        0L
                                                ));
                                                return;
                                            }
                                            int removed = removeMatchingItems(context.player(), listing.itemStack(), quantity);
                                            if (removed < quantity && removed > 0) {
                                                context.player().getInventory().add(listing.itemStack().copyWithCount(removed));
                                            }
                                            sendActionResult(context.player(), new ShopActionResultS2CPacket(
                                                    ShopActionResultS2CPacket.ActionType.SOLD,
                                                    listing.itemStack().getHoverName().getString(),
                                                    quantity,
                                                    listing.priceSell() == null ? 0L : listing.priceSell(),
                                                    0L
                                            ));
                                            syncShop(context.player(), menu.getShopId(), menu.isAdminShop(), menu.isOwnerOrOperator());
                                        }));
                            });
                        }));
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(AddListingC2SPacket.TYPE, (payload, context) -> {
            ShopBrowseScreenHandler menu = activeMenu(context.player());
            if (menu == null || !menu.isOwnerOrOperator()) {
                context.server().execute(() -> context.player().sendSystemMessage(Component.translatable("economy_ld.shop.listing.add.error.not_owner")));
                return;
            }
            ItemStack item = ItemStack.parseOptional(context.player().registryAccess(), payload.itemNbt());
            if (item.isEmpty()) {
                context.server().execute(() -> context.player().sendSystemMessage(Component.translatable("economy_ld.shop.listing.add.error.empty_item")));
                return;
            }
            Long buy = normalizePrice(payload.priceBuy());
            Long sell = normalizePrice(payload.priceSell());
            if (buy == null && sell == null) {
                context.server().execute(() -> context.player().sendSystemMessage(Component.translatable("economy_ld.shop.listing.add.error.no_price")));
                return;
            }
            EconomyManager.getInstance().addListing(menu.getShopId(), item, buy, sell, payload.perOp(), payload.buyCap())
                    .whenComplete((ignored, error) -> context.server().execute(() -> {
                        if (error != null) {
                            context.player().sendSystemMessage(Component.translatable("economy_ld.shop.listing.add.error.failed", error.getMessage()));
                            return;
                        }
                        context.player().sendSystemMessage(Component.translatable("economy_ld.shop.listing.add.success"));
                        syncShop(context.player(), menu.getShopId(), menu.isAdminShop(), menu.isOwnerOrOperator());
                    }));
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateListingC2SPacket.TYPE, (payload, context) -> {
            ShopBrowseScreenHandler menu = activeMenu(context.player());
            if (menu == null || !menu.isOwnerOrOperator()) {
                return;
            }
            Long buy = normalizePrice(payload.priceBuy());
            Long sell = normalizePrice(payload.priceSell());
            if (buy == null && sell == null) {
                context.server().execute(() -> context.player().sendSystemMessage(Component.translatable("economy_ld.shop.listing.update.error.no_price")));
                return;
            }
            EconomyManager.getInstance().getListings(menu.getShopId()).whenComplete((listings, error) -> {
                if (error != null) {
                    return;
                }
                listings.stream().filter(l -> l.id().equals(payload.listingId())).findFirst().ifPresent(listing ->
                        EconomyManager.getInstance().updateListing(listing.id(), buy, sell, payload.perOp(), payload.buyCap())
                                .whenComplete((ignored, err2) -> context.server().execute(() -> {
                                    if (err2 != null) {
                                        return;
                                    }
                                    syncShop(context.player(), menu.getShopId(), menu.isAdminShop(), menu.isOwnerOrOperator());
                                }))
                );
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RemoveListingC2SPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ShopBrowseScreenHandler menu = activeMenu(context.player());
                if (menu == null || !menu.isOwnerOrOperator()) {
                    return;
                }
                EconomyManager manager = EconomyManager.getInstance();
                manager.getListings(menu.getShopId()).whenComplete((listings, error) ->
                        context.server().execute(() -> {
                            if (error != null) {
                                return;
                            }
                            listings.stream().filter(l -> l.id().equals(payload.listingId())).findFirst().ifPresent(listing -> {
                                long stock = listing.stock() == null ? 0L : listing.stock();
                                Runnable removeAndSync = () -> manager.removeListing(listing.id())
                                        .whenComplete((ignored, err2) -> context.server().execute(() -> {
                                            if (err2 != null) {
                                                return;
                                            }
                                            syncShop(context.player(), menu.getShopId(), menu.isAdminShop(), menu.isOwnerOrOperator());
                                        }));

                                if (stock <= 0L) {
                                    removeAndSync.run();
                                    return;
                                }

                                manager.getShop(menu.getShopId()).whenComplete((shopOpt, shopError) ->
                                        context.server().execute(() -> {
                                            if (shopError != null) {
                                                EconomyLdMod.LOGGER.warn("Failed to load shop {} before removing listing {}",
                                                        menu.getShopId(), listing.id(), shopError);
                                                removeAndSync.run();
                                                return;
                                            }
                                            if (shopOpt.isPresent() && shopOpt.get().ownerUuid() != null) {
                                                ServerPlayer owner = context.server().getPlayerList().getPlayer(shopOpt.get().ownerUuid());
                                                if (owner != null) {
                                                    giveOrDrop(owner, listing.itemStack(), stock);
                                                } else {
                                                    EconomyLdMod.LOGGER.warn("Shop listing removed but owner {} is offline — {} x {} stock was lost",
                                                            shopOpt.get().ownerUuid(), stock, listing.itemStack().getHoverName().getString());
                                                }
                                            }
                                            removeAndSync.run();
                                        }));
                            });
                        }));
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RestockListingC2SPacket.TYPE, (payload, context) -> {
            ShopBrowseScreenHandler menu = activeMenu(context.player());
            if (menu == null || !menu.isOwnerOrOperator()) {
                return;
            }
            EconomyManager.getInstance().getListings(menu.getShopId()).whenComplete((listings, error) -> {
                if (error != null) {
                    return;
                }
                listings.stream().filter(l -> l.id().equals(payload.listingId())).findFirst().ifPresent(listing ->
                {
                    int quantity = Math.max(1, payload.quantity());
                    int removed = removeMatchingItems(context.player(), listing.itemStack(), quantity);
                    if (removed < quantity) {
                        if (removed > 0) {
                            context.player().getInventory().add(listing.itemStack().copyWithCount(removed));
                        }
                        return;
                    }
                    EconomyManager.getInstance().restockListing(listing.id(), quantity)
                            .whenComplete((success, err2) -> context.server().execute(() -> {
                                if (err2 != null || !Boolean.TRUE.equals(success)) {
                                    context.player().getInventory().add(listing.itemStack().copyWithCount(quantity));
                                    return;
                                }
                                context.player().inventoryMenu.sendAllDataToRemote();
                                sendActionResult(context.player(), new ShopActionResultS2CPacket(
                                        ShopActionResultS2CPacket.ActionType.RESTOCKED,
                                        listing.itemStack().getHoverName().getString(),
                                        quantity,
                                        0L,
                                        0L
                                ));
                                syncShop(context.player(), menu.getShopId(), menu.isAdminShop(), menu.isOwnerOrOperator());
                            }));
                }
                );
            });
        });
    }

    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(ShopListingsSyncS2CPacket.TYPE, (payload, context) ->
                ShopClientState.setListings(payload.shopId(), payload.adminShop(), payload.ownerOrOperator(), payload.ownerLabel(), payload.openerBalance(), payload.listings()));
        ClientPlayNetworking.registerGlobalReceiver(ShopActionResultS2CPacket.TYPE, (payload, context) ->
                ShopClientState.setLastActionResult(payload));
    }

    public static void sendListingsSync(ServerPlayer player, ShopListingsSyncS2CPacket payload) {
        ServerPlayNetworking.send(player, payload);
    }

    private static void sendActionResult(ServerPlayer player, ShopActionResultS2CPacket packet) {
        ServerPlayNetworking.send(player, packet);
    }

    public static void syncShop(ServerPlayer player, UUID shopId, boolean adminShop, boolean ownerOrOperator) {
        EconomyManager manager = EconomyManager.getInstance();
        CompletableFuture<String> ownerLabelFuture;
        if (adminShop) {
            ownerLabelFuture = CompletableFuture.completedFuture("Server");
        } else {
            ownerLabelFuture = manager.getShop(shopId).thenCompose(shop -> {
                if (shop.isEmpty() || shop.get().ownerUuid() == null) {
                    return CompletableFuture.completedFuture("Unknown");
                }
                return manager.getUsernameByUuid(shop.get().ownerUuid()).thenApply(name -> name.orElse("Unknown"));
            });
        }

        CompletableFuture<List<net.ledok.economy_ld.shop.ShopListing>> listingsFuture = manager.getListings(shopId);
        CompletableFuture<Long> balanceFuture = manager.getBalance(player.getUUID(), player.getName().getString());
        ownerLabelFuture.thenCombine(listingsFuture, OwnerAndListings::new)
                .thenCombine(balanceFuture, (pair, balance) ->
                        new ShopListingsSyncS2CPacket(shopId, adminShop, ownerOrOperator, pair.ownerLabel(), balance, pair.listings()))
                .whenComplete((payload, error) -> player.server.execute(() -> {
                    if (error != null) {
                        return;
                    }
                    sendListingsSync(player, payload);
                }));
    }

    private record OwnerAndListings(String ownerLabel, List<net.ledok.economy_ld.shop.ShopListing> listings) {
    }

    private static Long normalizePrice(Long raw) {
        if (raw == null || raw <= 0) {
            return null;
        }
        return raw;
    }

    private static ShopBrowseScreenHandler activeMenu(ServerPlayer player) {
        return player.containerMenu instanceof ShopBrowseScreenHandler handler ? handler : null;
    }

    private static int countMatchingItems(ServerPlayer player, ItemStack template) {
        ItemStack normalizedTemplate;
        try {
            String encoded = ItemStackSerializationUtil.toBase64(template, player.registryAccess());
            normalizedTemplate = ItemStackSerializationUtil.fromBase64(encoded, player.registryAccess());
        } catch (Exception e) {
            EconomyLdMod.LOGGER.warn("Failed to normalize template for counting, falling back to original", e);
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
            EconomyLdMod.LOGGER.warn("Failed to normalize template for matching, falling back to original", e);
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

    private static void giveOrDrop(ServerPlayer player, ItemStack template, long totalAmount) {
        long remaining = totalAmount;
        int maxStack = Math.max(1, template.getMaxStackSize());
        while (remaining > 0) {
            int toGiveCount = (int) Math.min(remaining, maxStack);
            ItemStack toGive = template.copyWithCount(toGiveCount);
            boolean added = player.getInventory().add(toGive);
            if (!added && !toGive.isEmpty()) {
                player.drop(toGive, false);
            }
            remaining -= toGiveCount;
        }
    }
}
