package net.ledok.economy_ld.db;

import net.ledok.economy_ld.auction.AuctionRecord;
import net.ledok.economy_ld.auction.PendingDelivery;
import net.ledok.economy_ld.shop.ShopRecord;
import net.ledok.economy_ld.shop.ShopListing;
import net.ledok.economy_ld.util.ItemStackSerializationUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public abstract class AbstractJdbcEconomyDatabase implements EconomyDatabase {
    protected final ExecutorService executor;

    protected AbstractJdbcEconomyDatabase(ExecutorService executor) {
        this.executor = executor;
    }

    protected abstract Connection connection() throws SQLException;

    protected abstract String transactionsSchema();

    protected abstract String pendingDeliveriesSchema();

    protected abstract String upsertShopSql();

    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = connection();
                 Statement statement = conn.createStatement()) {
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS wallets (
                            uuid        VARCHAR(36) PRIMARY KEY,
                            username    VARCHAR(16) NOT NULL,
                            balance     BIGINT NOT NULL DEFAULT 0
                        )
                        """);
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS shops (
                            id          VARCHAR(36) PRIMARY KEY,
                            owner_uuid  VARCHAR(36),
                            is_admin    TINYINT(1) NOT NULL,
                            world       VARCHAR(64) NOT NULL,
                            x INT, y INT, z INT,
                            created_at  BIGINT NOT NULL
                        )
                        """);
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS shop_listings (
                            id          VARCHAR(36) PRIMARY KEY,
                            shop_id     VARCHAR(36) NOT NULL,
                            item_nbt    TEXT NOT NULL,
                            price_buy   BIGINT,
                            price_sell  BIGINT,
                            per_op      INT NOT NULL DEFAULT 1,
                            buy_cap     BIGINT,
                            stock       BIGINT,
                            FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE
                        )
                        """);
                ensureColumnExists(conn, "shop_listings", "per_op", "INT NOT NULL DEFAULT 1");
                ensureColumnExists(conn, "shop_listings", "buy_cap", "BIGINT");
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS auctions (
                            id            VARCHAR(36) PRIMARY KEY,
                            seller_uuid   VARCHAR(36) NOT NULL,
                            item_nbt      TEXT NOT NULL,
                            quantity      INT NOT NULL DEFAULT 1,
                            start_price   BIGINT NOT NULL,
                            buyout_price  BIGINT,
                            current_bid   BIGINT NOT NULL,
                            bidder_uuid   VARCHAR(36),
                            expires_at    BIGINT NOT NULL,
                            status        VARCHAR(16) NOT NULL
                        )
                        """);
                ensureColumnExists(conn, "auctions", "quantity", "INT NOT NULL DEFAULT 1");
                ensureColumnExists(conn, "auctions", "buyout_price", "BIGINT");
                statement.executeUpdate(pendingDeliveriesSchema());
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS player_auction_limits (
                            uuid         VARCHAR(36) PRIMARY KEY,
                            bonus        INT NOT NULL DEFAULT 0
                        )
                        """);
                statement.executeUpdate(transactionsSchema());
            } catch (SQLException e) {
                throw new RuntimeException("Failed to initialize schema", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Long> getBalance(UUID uuid, String username) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = connection()) {
                upsertWallet(conn, uuid, username);
                try (PreparedStatement ps = conn.prepareStatement("SELECT balance FROM wallets WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return rs.getLong("balance");
                        }
                    }
                }
                return 0L;
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get balance for uuid=" + uuid, e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<OptionalLong> getBalanceByUsername(String username) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement("SELECT balance FROM wallets WHERE LOWER(username) = LOWER(?)")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return OptionalLong.of(rs.getLong("balance"));
                    }
                }
                return OptionalLong.empty();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get balance for username=" + username, e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<UUID>> getUuidByUsername(String username) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement("SELECT uuid FROM wallets WHERE LOWER(username) = LOWER(?)")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(UUID.fromString(rs.getString("uuid")));
                    }
                }
                return Optional.empty();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get uuid for username=" + username, e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<String>> getUsernameByUuid(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement("SELECT username FROM wallets WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.ofNullable(rs.getString("username"));
                    }
                }
                return Optional.empty();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get username for uuid=" + uuid, e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<String>> getAllUsernames() {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement("SELECT username FROM wallets ORDER BY username ASC");
                 ResultSet rs = ps.executeQuery()) {
                List<String> usernames = new ArrayList<>();
                while (rs.next()) {
                    String username = rs.getString("username");
                    if (username != null && !username.isBlank()) {
                        usernames.add(username);
                    }
                }
                return usernames;
            } catch (SQLException e) {
                throw new RuntimeException("Failed to load known usernames", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> setBalance(UUID uuid, String username, long balance) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = connection()) {
                upsertWallet(conn, uuid, username);
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE wallets SET username = ?, balance = ? WHERE uuid = ?")) {
                    ps.setString(1, username);
                    ps.setLong(2, balance);
                    ps.setString(3, uuid.toString());
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to set balance for uuid=" + uuid, e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> addBalance(UUID uuid, String username, long delta) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = connection()) {
                upsertWallet(conn, uuid, username);
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE wallets SET username = ?, balance = balance + ? WHERE uuid = ?")) {
                    ps.setString(1, username);
                    ps.setLong(2, delta);
                    ps.setString(3, uuid.toString());
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to add balance for uuid=" + uuid, e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> transfer(UUID fromUuid, String fromUsername, UUID toUuid, String toUsername, long amount) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = connection()) {
                conn.setAutoCommit(false);
                try {
                    upsertWallet(conn, fromUuid, fromUsername);
                    upsertWallet(conn, toUuid, toUsername);

                    long fromBalance;
                    try (PreparedStatement ps = conn.prepareStatement("SELECT balance FROM wallets WHERE uuid = ?")) {
                        ps.setString(1, fromUuid.toString());
                        try (ResultSet rs = ps.executeQuery()) {
                            fromBalance = rs.next() ? rs.getLong("balance") : 0L;
                        }
                    }
                    if (fromBalance < amount) {
                        conn.rollback();
                        return false;
                    }

                    try (PreparedStatement debit = conn.prepareStatement("UPDATE wallets SET balance = balance - ? WHERE uuid = ?");
                         PreparedStatement credit = conn.prepareStatement("UPDATE wallets SET balance = balance + ? WHERE uuid = ?")) {
                        debit.setLong(1, amount);
                        debit.setString(2, fromUuid.toString());
                        debit.executeUpdate();

                        credit.setLong(1, amount);
                        credit.setString(2, toUuid.toString());
                        credit.executeUpdate();
                    }
                    conn.commit();
                    return true;
                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to transfer funds", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> upsertShop(UUID shopId, UUID ownerUuid, boolean isAdmin, ResourceLocation dimension, BlockPos pos) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement(upsertShopSql())) {
                ps.setString(1, shopId.toString());
                if (ownerUuid == null) {
                    ps.setNull(2, java.sql.Types.VARCHAR);
                } else {
                    ps.setString(2, ownerUuid.toString());
                }
                ps.setBoolean(3, isAdmin);
                ps.setString(4, dimension.toString());
                ps.setInt(5, pos.getX());
                ps.setInt(6, pos.getY());
                ps.setInt(7, pos.getZ());
                ps.setLong(8, System.currentTimeMillis() / 1000L);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to upsert shop " + shopId, e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<ShopRecord>> getShop(UUID shopId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement("SELECT * FROM shops WHERE id = ?")) {
                ps.setString(1, shopId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }

                    String ownerRaw = rs.getString("owner_uuid");
                    UUID ownerUuid = ownerRaw == null ? null : UUID.fromString(ownerRaw);
                    boolean isAdmin = rs.getBoolean("is_admin");
                    ResourceLocation dimension = ResourceLocation.parse(rs.getString("world"));
                    BlockPos pos = new BlockPos(rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
                    long createdAt = rs.getLong("created_at");
                    return Optional.of(new ShopRecord(shopId, ownerUuid, isAdmin, dimension, pos, createdAt));
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to load shop " + shopId, e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> deleteShop(UUID shopId) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM shops WHERE id = ?")) {
                ps.setString(1, shopId.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to delete shop " + shopId, e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> addListing(UUID shopId, ItemStack item, Long priceBuy, Long priceSell, int perOp, Long buyCap) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement("""
                         INSERT INTO shop_listings (id, shop_id, item_nbt, price_buy, price_sell, per_op, buy_cap, stock)
                         VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                         """)) {
                boolean adminShop = isAdminShop(conn, shopId);
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, shopId.toString());
                ps.setString(3, ItemStackSerializationUtil.toBase64(item));
                if (priceBuy == null) {
                    ps.setNull(4, java.sql.Types.BIGINT);
                } else {
                    ps.setLong(4, priceBuy);
                }
                if (priceSell == null) {
                    ps.setNull(5, java.sql.Types.BIGINT);
                } else {
                    ps.setLong(5, priceSell);
                }
                ps.setInt(6, Math.max(1, perOp));
                if (buyCap == null) {
                    ps.setNull(7, java.sql.Types.BIGINT);
                } else {
                    ps.setLong(7, buyCap);
                }
                if (adminShop) {
                    ps.setNull(8, java.sql.Types.BIGINT);
                } else {
                    ps.setLong(8, 0L);
                }
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to add listing to shop " + shopId, e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<ShopListing>> getListings(UUID shopId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement("SELECT * FROM shop_listings WHERE shop_id = ?")) {
                ps.setString(1, shopId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    List<ShopListing> listings = new ArrayList<>();
                    while (rs.next()) {
                        UUID id = UUID.fromString(rs.getString("id"));
                        ItemStack stack = ItemStackSerializationUtil.fromBase64(rs.getString("item_nbt"));
                        Long priceBuy = rs.getObject("price_buy") == null ? null : rs.getLong("price_buy");
                        Long priceSell = rs.getObject("price_sell") == null ? null : rs.getLong("price_sell");
                        int perOp = rs.getInt("per_op");
                        Long buyCap = rs.getObject("buy_cap") == null ? null : rs.getLong("buy_cap");
                        Long stock = rs.getObject("stock") == null ? null : rs.getLong("stock");
                        listings.add(new ShopListing(id, shopId, stack, priceBuy, priceSell, perOp, buyCap, stock));
                    }
                    return listings;
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to load listings for shop " + shopId, e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> removeListing(UUID listingId) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM shop_listings WHERE id = ?")) {
                ps.setString(1, listingId.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to remove listing " + listingId, e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> updateListing(UUID listingId, Long priceBuy, Long priceSell, int perOp, Long buyCap) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement("""
                         UPDATE shop_listings
                         SET price_buy = ?, price_sell = ?, per_op = ?, buy_cap = ?
                         WHERE id = ?
                         """)) {
                if (priceBuy == null) {
                    ps.setNull(1, java.sql.Types.BIGINT);
                } else {
                    ps.setLong(1, priceBuy);
                }
                if (priceSell == null) {
                    ps.setNull(2, java.sql.Types.BIGINT);
                } else {
                    ps.setLong(2, priceSell);
                }
                ps.setInt(3, Math.max(1, perOp));
                if (buyCap == null) {
                    ps.setNull(4, java.sql.Types.BIGINT);
                } else {
                    ps.setLong(4, buyCap);
                }
                ps.setString(5, listingId.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to update listing " + listingId, e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> restockListing(UUID listingId, int quantity) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement("""
                         UPDATE shop_listings
                         SET stock = COALESCE(stock, 0) + ?
                         WHERE id = ?
                         """)) {
                ps.setInt(1, Math.max(1, quantity));
                ps.setString(2, listingId.toString());
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new RuntimeException("Failed to restock listing " + listingId, e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> buyItem(UUID listingId, UUID buyerUuid, String buyerUsername, int quantity) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = connection()) {
                conn.setAutoCommit(false);
                try {
                    upsertWallet(conn, buyerUuid, buyerUsername);

                    long priceBuy;
                    Long stock;
                    try (PreparedStatement q = conn.prepareStatement("SELECT price_buy, stock FROM shop_listings WHERE id = ?")) {
                        q.setString(1, listingId.toString());
                        try (ResultSet rs = q.executeQuery()) {
                            if (!rs.next()) {
                                conn.rollback();
                                return false;
                            }
                            if (rs.getObject("price_buy") == null) {
                                conn.rollback();
                                return false;
                            }
                            priceBuy = rs.getLong("price_buy");
                            stock = rs.getObject("stock") == null ? null : rs.getLong("stock");
                        }
                    }

                    long total = priceBuy;
                    long balance;
                    try (PreparedStatement q = conn.prepareStatement("SELECT balance FROM wallets WHERE uuid = ?")) {
                        q.setString(1, buyerUuid.toString());
                        try (ResultSet rs = q.executeQuery()) {
                            balance = rs.next() ? rs.getLong("balance") : 0L;
                        }
                    }
                    if (balance < total) {
                        conn.rollback();
                        return false;
                    }
                    if (stock != null && stock < quantity) {
                        conn.rollback();
                        return false;
                    }

                    try (PreparedStatement debit = conn.prepareStatement("UPDATE wallets SET balance = balance - ? WHERE uuid = ?")) {
                        debit.setLong(1, total);
                        debit.setString(2, buyerUuid.toString());
                        debit.executeUpdate();
                    }
                    UUID ownerUuid = findShopOwnerUuidForListing(conn, listingId);
                    if (ownerUuid != null) {
                        ensureWalletExists(conn, ownerUuid);
                        try (PreparedStatement creditOwner = conn.prepareStatement("UPDATE wallets SET balance = balance + ? WHERE uuid = ?")) {
                            creditOwner.setLong(1, total);
                            creditOwner.setString(2, ownerUuid.toString());
                            creditOwner.executeUpdate();
                        }
                    }
                    if (stock != null) {
                        try (PreparedStatement dec = conn.prepareStatement("UPDATE shop_listings SET stock = stock - ? WHERE id = ?")) {
                            dec.setInt(1, quantity);
                            dec.setString(2, listingId.toString());
                            dec.executeUpdate();
                        }
                    }

                    conn.commit();
                    return true;
                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to buy from listing " + listingId, e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> sellItem(UUID listingId, UUID sellerUuid, String sellerUsername, int quantity) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = connection()) {
                conn.setAutoCommit(false);
                try {
                    upsertWallet(conn, sellerUuid, sellerUsername);

                    long priceSell;
                    Long stock;
                    Long buyCap;
                    try (PreparedStatement q = conn.prepareStatement("SELECT price_sell, stock, buy_cap FROM shop_listings WHERE id = ?")) {
                        q.setString(1, listingId.toString());
                        try (ResultSet rs = q.executeQuery()) {
                            if (!rs.next()) {
                                conn.rollback();
                                return false;
                            }
                            if (rs.getObject("price_sell") == null) {
                                conn.rollback();
                                return false;
                            }
                            priceSell = rs.getLong("price_sell");
                            stock = rs.getObject("stock") == null ? null : rs.getLong("stock");
                            buyCap = rs.getObject("buy_cap") == null ? null : rs.getLong("buy_cap");
                        }
                    }

                    long total = priceSell;
                    boolean adminShop = isAdminShopForListing(conn, listingId);
                    if (adminShop) {
                        if (buyCap == null || buyCap < quantity) {
                            conn.rollback();
                            return false;
                        }
                    } else if (stock != null && buyCap != null && stock + quantity > buyCap) {
                        conn.rollback();
                        return false;
                    }

                    UUID ownerUuid = findShopOwnerUuidForListing(conn, listingId);
                    if (!adminShop && ownerUuid != null) {
                        ensureWalletExists(conn, ownerUuid);
                        long ownerBalance;
                        try (PreparedStatement q = conn.prepareStatement("SELECT balance FROM wallets WHERE uuid = ?")) {
                            q.setString(1, ownerUuid.toString());
                            try (ResultSet rs = q.executeQuery()) {
                                ownerBalance = rs.next() ? rs.getLong("balance") : 0L;
                            }
                        }
                        if (ownerBalance < total) {
                            conn.rollback();
                            return false;
                        }
                        try (PreparedStatement debitOwner = conn.prepareStatement("UPDATE wallets SET balance = balance - ? WHERE uuid = ?")) {
                            debitOwner.setLong(1, total);
                            debitOwner.setString(2, ownerUuid.toString());
                            debitOwner.executeUpdate();
                        }
                    }
                    try (PreparedStatement credit = conn.prepareStatement("UPDATE wallets SET balance = balance + ? WHERE uuid = ?")) {
                        credit.setLong(1, total);
                        credit.setString(2, sellerUuid.toString());
                        credit.executeUpdate();
                    }
                    if (adminShop) {
                        try (PreparedStatement decCap = conn.prepareStatement("UPDATE shop_listings SET buy_cap = buy_cap - ? WHERE id = ?")) {
                            decCap.setInt(1, quantity);
                            decCap.setString(2, listingId.toString());
                            decCap.executeUpdate();
                        }
                    } else if (stock != null) {
                        try (PreparedStatement inc = conn.prepareStatement("UPDATE shop_listings SET stock = stock + ? WHERE id = ?")) {
                            inc.setInt(1, quantity);
                            inc.setString(2, listingId.toString());
                            inc.executeUpdate();
                        }
                    }

                    conn.commit();
                    return true;
                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to sell to listing " + listingId, e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> placeAuction(
            UUID sellerUuid,
            String sellerName,
            ItemStack item,
            int quantity,
            long startPrice,
            Long buyoutPrice,
            long expiresAt,
            int listingFeePercent,
            int maxListings,
            RegistryAccess registryAccess
    ) {
        return CompletableFuture.supplyAsync(() -> {
            int normalizedQuantity = Math.max(1, quantity);
            long normalizedStartPrice = Math.max(1L, startPrice);
            int normalizedListingLimit = Math.max(1, maxListings);
            int normalizedFeePercent = Math.max(0, Math.min(100, listingFeePercent));
            long listingFee = (normalizedStartPrice * normalizedFeePercent) / 100L;

            try (Connection conn = connection()) {
                conn.setAutoCommit(false);
                try {
                    upsertWallet(conn, sellerUuid, sellerName);

                    int activeCount = 0;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT COUNT(*) AS cnt FROM auctions WHERE seller_uuid = ? AND status = 'ACTIVE'")) {
                        ps.setString(1, sellerUuid.toString());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                activeCount = rs.getInt("cnt");
                            }
                        }
                    }
                    if (activeCount >= normalizedListingLimit) {
                        conn.rollback();
                        return false;
                    }

                    long balance = 0L;
                    try (PreparedStatement ps = conn.prepareStatement("SELECT balance FROM wallets WHERE uuid = ?")) {
                        ps.setString(1, sellerUuid.toString());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                balance = rs.getLong("balance");
                            }
                        }
                    }
                    if (balance < listingFee) {
                        conn.rollback();
                        return false;
                    }
                    if (listingFee > 0) {
                        try (PreparedStatement debit = conn.prepareStatement("UPDATE wallets SET balance = balance - ? WHERE uuid = ?")) {
                            debit.setLong(1, listingFee);
                            debit.setString(2, sellerUuid.toString());
                            debit.executeUpdate();
                        }
                    }

                    try (PreparedStatement insert = conn.prepareStatement("""
                            INSERT INTO auctions (id, seller_uuid, item_nbt, quantity, start_price, buyout_price, current_bid, bidder_uuid, expires_at, status)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """)) {
                        insert.setString(1, UUID.randomUUID().toString());
                        insert.setString(2, sellerUuid.toString());
                        insert.setString(3, ItemStackSerializationUtil.toBase64(item, registryAccess));
                        insert.setInt(4, normalizedQuantity);
                        insert.setLong(5, normalizedStartPrice);
                        if (buyoutPrice == null) {
                            insert.setNull(6, java.sql.Types.BIGINT);
                        } else {
                            insert.setLong(6, Math.max(1L, buyoutPrice));
                        }
                        insert.setLong(7, normalizedStartPrice);
                        insert.setNull(8, java.sql.Types.VARCHAR);
                        insert.setLong(9, expiresAt);
                        insert.setString(10, "ACTIVE");
                        insert.executeUpdate();
                    }

                    conn.commit();
                    return true;
                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to place auction", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> placeBid(UUID auctionId, UUID bidderUuid, String bidderName, long bidAmount) {
        return CompletableFuture.supplyAsync(() -> {
            long normalizedBid = Math.max(1L, bidAmount);
            try (Connection conn = connection()) {
                conn.setAutoCommit(false);
                try {
                    upsertWallet(conn, bidderUuid, bidderName);

                    UUID sellerUuid;
                    long currentBid;
                    String previousBidderRaw;
                    String status;
                    long expiresAt;
                    try (PreparedStatement ps = conn.prepareStatement("""
                            SELECT seller_uuid, current_bid, bidder_uuid, status, expires_at
                            FROM auctions WHERE id = ?
                            """)) {
                        ps.setString(1, auctionId.toString());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) {
                                conn.rollback();
                                return false;
                            }
                            sellerUuid = UUID.fromString(rs.getString("seller_uuid"));
                            currentBid = rs.getLong("current_bid");
                            previousBidderRaw = rs.getString("bidder_uuid");
                            status = rs.getString("status");
                            expiresAt = rs.getLong("expires_at");
                        }
                    }
                    if (!"ACTIVE".equalsIgnoreCase(status) || expiresAt <= (System.currentTimeMillis() / 1000L)) {
                        conn.rollback();
                        return false;
                    }
                    if (bidderUuid.equals(sellerUuid)) {
                        conn.rollback();
                        return false;
                    }
                    if (normalizedBid <= currentBid) {
                        conn.rollback();
                        return false;
                    }

                    long bidderBalance = 0L;
                    try (PreparedStatement ps = conn.prepareStatement("SELECT balance FROM wallets WHERE uuid = ?")) {
                        ps.setString(1, bidderUuid.toString());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                bidderBalance = rs.getLong("balance");
                            }
                        }
                    }
                    if (bidderBalance < normalizedBid) {
                        conn.rollback();
                        return false;
                    }

                    try (PreparedStatement debit = conn.prepareStatement("UPDATE wallets SET balance = balance - ? WHERE uuid = ?")) {
                        debit.setLong(1, normalizedBid);
                        debit.setString(2, bidderUuid.toString());
                        debit.executeUpdate();
                    }

                    if (previousBidderRaw != null && !previousBidderRaw.isBlank()) {
                        UUID previousBidder = UUID.fromString(previousBidderRaw);
                        ensureWalletExists(conn, previousBidder);
                        try (PreparedStatement refund = conn.prepareStatement("UPDATE wallets SET balance = balance + ? WHERE uuid = ?")) {
                            refund.setLong(1, currentBid);
                            refund.setString(2, previousBidder.toString());
                            refund.executeUpdate();
                        }
                    }

                    try (PreparedStatement update = conn.prepareStatement(
                            "UPDATE auctions SET current_bid = ?, bidder_uuid = ? WHERE id = ?")) {
                        update.setLong(1, normalizedBid);
                        update.setString(2, bidderUuid.toString());
                        update.setString(3, auctionId.toString());
                        update.executeUpdate();
                    }

                    conn.commit();
                    return true;
                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to place bid", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> buyout(UUID auctionId, UUID buyerUuid, String buyerName, int serverTaxPercent) {
        return CompletableFuture.supplyAsync(() -> {
            int normalizedTax = Math.max(0, Math.min(100, serverTaxPercent));
            try (Connection conn = connection()) {
                conn.setAutoCommit(false);
                try {
                    upsertWallet(conn, buyerUuid, buyerName);

                    UUID sellerUuid;
                    String itemNbt;
                    int quantity;
                    Long buyoutPrice;
                    String status;
                    try (PreparedStatement ps = conn.prepareStatement("""
                            SELECT seller_uuid, item_nbt, quantity, buyout_price, status
                            FROM auctions WHERE id = ?
                            """)) {
                        ps.setString(1, auctionId.toString());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) {
                                conn.rollback();
                                return false;
                            }
                            sellerUuid = UUID.fromString(rs.getString("seller_uuid"));
                            itemNbt = rs.getString("item_nbt");
                            quantity = Math.max(1, rs.getInt("quantity"));
                            buyoutPrice = rs.getObject("buyout_price") == null ? null : rs.getLong("buyout_price");
                            status = rs.getString("status");
                        }
                    }
                    if (buyoutPrice == null || !"ACTIVE".equalsIgnoreCase(status)) {
                        conn.rollback();
                        return false;
                    }

                    long buyerBalance = 0L;
                    try (PreparedStatement ps = conn.prepareStatement("SELECT balance FROM wallets WHERE uuid = ?")) {
                        ps.setString(1, buyerUuid.toString());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                buyerBalance = rs.getLong("balance");
                            }
                        }
                    }
                    if (buyerBalance < buyoutPrice) {
                        conn.rollback();
                        return false;
                    }

                    try (PreparedStatement debit = conn.prepareStatement("UPDATE wallets SET balance = balance - ? WHERE uuid = ?")) {
                        debit.setLong(1, buyoutPrice);
                        debit.setString(2, buyerUuid.toString());
                        debit.executeUpdate();
                    }

                    long tax = (buyoutPrice * normalizedTax) / 100L;
                    long sellerPayout = Math.max(0L, buyoutPrice - tax);
                    ensureWalletExists(conn, sellerUuid);
                    if (sellerPayout > 0) {
                        try (PreparedStatement credit = conn.prepareStatement("UPDATE wallets SET balance = balance + ? WHERE uuid = ?")) {
                            credit.setLong(1, sellerPayout);
                            credit.setString(2, sellerUuid.toString());
                            credit.executeUpdate();
                        }
                    }

                    try (PreparedStatement update = conn.prepareStatement("""
                            UPDATE auctions
                            SET current_bid = ?, bidder_uuid = ?, status = ?
                            WHERE id = ?
                            """)) {
                        update.setLong(1, buyoutPrice);
                        update.setString(2, buyerUuid.toString());
                        update.setString(3, "SOLD");
                        update.setString(4, auctionId.toString());
                        update.executeUpdate();
                    }

                    enqueuePendingItem(conn, buyerUuid, itemNbt, quantity, "AUCTION_BUYOUT_ITEM");

                    conn.commit();
                    return true;
                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to buyout auction", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> cancelAuction(UUID auctionId, UUID requesterUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = connection()) {
                conn.setAutoCommit(false);
                try {
                    String sellerUuidRaw;
                    String bidderUuidRaw;
                    String itemNbt;
                    int quantity;
                    String status;
                    try (PreparedStatement ps = conn.prepareStatement("""
                            SELECT seller_uuid, bidder_uuid, item_nbt, quantity, status
                            FROM auctions WHERE id = ?
                            """)) {
                        ps.setString(1, auctionId.toString());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) {
                                conn.rollback();
                                return false;
                            }
                            sellerUuidRaw = rs.getString("seller_uuid");
                            bidderUuidRaw = rs.getString("bidder_uuid");
                            itemNbt = rs.getString("item_nbt");
                            quantity = Math.max(1, rs.getInt("quantity"));
                            status = rs.getString("status");
                        }
                    }
                    if (!"ACTIVE".equalsIgnoreCase(status)) {
                        conn.rollback();
                        return false;
                    }
                    if (sellerUuidRaw == null) {
                        conn.rollback();
                        return false;
                    }
                    if (bidderUuidRaw != null && !bidderUuidRaw.isBlank()) {
                        conn.rollback();
                        return false;
                    }

                    enqueuePendingItem(conn, UUID.fromString(sellerUuidRaw), itemNbt, quantity, "AUCTION_CANCELLED_RETURN");

                    try (PreparedStatement update = conn.prepareStatement("UPDATE auctions SET status = ? WHERE id = ?")) {
                        update.setString(1, "CANCELLED");
                        update.setString(2, auctionId.toString());
                        update.executeUpdate();
                    }

                    conn.commit();
                    return true;
                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to cancel auction", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<AuctionRecord>> getActiveAuctions(RegistryAccess registryAccess) {
        return loadAuctionsByWhere("a.status = 'ACTIVE'", null, registryAccess);
    }

    @Override
    public CompletableFuture<List<AuctionRecord>> getPlayerAuctions(UUID sellerUuid, RegistryAccess registryAccess) {
        return loadAuctionsByWhere("a.status = 'ACTIVE' AND a.seller_uuid = ?", sellerUuid, registryAccess);
    }

    @Override
    public CompletableFuture<List<AuctionRecord>> getPlayerBids(UUID bidderUuid, RegistryAccess registryAccess) {
        return loadAuctionsByWhere("a.status = 'ACTIVE' AND a.bidder_uuid = ?", bidderUuid, registryAccess);
    }

    @Override
    public CompletableFuture<Void> processExpiredAuctions(int serverTaxPercent, RegistryAccess registryAccess) {
        return CompletableFuture.runAsync(() -> {
            int normalizedTax = Math.max(0, Math.min(100, serverTaxPercent));
            long now = System.currentTimeMillis() / 1000L;
            try (Connection conn = connection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement ps = conn.prepareStatement("""
                        SELECT id, seller_uuid, item_nbt, quantity, current_bid, bidder_uuid
                        FROM auctions
                        WHERE status = 'ACTIVE' AND expires_at <= ?
                        """)) {
                    ps.setLong(1, now);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            UUID auctionId = UUID.fromString(rs.getString("id"));
                            UUID sellerUuid = UUID.fromString(rs.getString("seller_uuid"));
                            String itemNbt = rs.getString("item_nbt");
                            int quantity = Math.max(1, rs.getInt("quantity"));
                            long currentBid = rs.getLong("current_bid");
                            String bidderRaw = rs.getString("bidder_uuid");

                            if (bidderRaw == null || bidderRaw.isBlank()) {
                                enqueuePendingItem(conn, sellerUuid, itemNbt, quantity, "AUCTION_EXPIRED_RETURN");
                                try (PreparedStatement update = conn.prepareStatement("UPDATE auctions SET status = ? WHERE id = ?")) {
                                    update.setString(1, "EXPIRED");
                                    update.setString(2, auctionId.toString());
                                    update.executeUpdate();
                                }
                                continue;
                            }

                            UUID bidderUuid = UUID.fromString(bidderRaw);
                            long tax = (currentBid * normalizedTax) / 100L;
                            long payout = Math.max(0L, currentBid - tax);
                            if (payout > 0) {
                                enqueuePendingLc(conn, sellerUuid, payout, "AUCTION_SOLD_PAYOUT");
                            }
                            enqueuePendingItem(conn, bidderUuid, itemNbt, quantity, "AUCTION_WON_ITEM");
                            try (PreparedStatement update = conn.prepareStatement("UPDATE auctions SET status = ? WHERE id = ?")) {
                                update.setString(1, "SOLD");
                                update.setString(2, auctionId.toString());
                                update.executeUpdate();
                            }
                        }
                    }
                    conn.commit();
                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to process expired auctions", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<PendingDelivery>> claimPendingDeliveries(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<PendingDelivery> deliveries = new ArrayList<>();
            try (Connection conn = connection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement ps = conn.prepareStatement("""
                        SELECT item_nbt, quantity, lc_amount, reason
                        FROM pending_deliveries
                        WHERE player_uuid = ?
                        ORDER BY id ASC
                        """)) {
                    ps.setString(1, playerUuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String itemNbt = rs.getString("item_nbt");
                            ItemStack stack = itemNbt == null ? null : ItemStackSerializationUtil.fromBase64(itemNbt);
                            int quantity = Math.max(0, rs.getInt("quantity"));
                            Long lcAmount = rs.getObject("lc_amount") == null ? null : rs.getLong("lc_amount");
                            String reason = rs.getString("reason");
                            deliveries.add(new PendingDelivery(playerUuid, stack, quantity, lcAmount, reason));
                        }
                    }
                    try (PreparedStatement delete = conn.prepareStatement("DELETE FROM pending_deliveries WHERE player_uuid = ?")) {
                        delete.setString(1, playerUuid.toString());
                        delete.executeUpdate();
                    }
                    conn.commit();
                    return deliveries;
                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to claim pending deliveries", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Integer> getEffectiveListingLimit(UUID playerUuid, int defaultLimit) {
        return CompletableFuture.supplyAsync(() -> {
            int bonus = 0;
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement("SELECT bonus FROM player_auction_limits WHERE uuid = ?")) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        bonus = rs.getInt("bonus");
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to read auction listing limit bonus", e);
            }
            return Math.max(1, defaultLimit + bonus);
        }, executor);
    }

    @Override
    public CompletableFuture<Void> setAuctionBonusLimit(UUID playerUuid, int bonus) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = connection()) {
                int normalizedBonus = Math.max(0, bonus);
                boolean exists;
                try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM player_auction_limits WHERE uuid = ?")) {
                    ps.setString(1, playerUuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        exists = rs.next();
                    }
                }
                if (exists) {
                    try (PreparedStatement update = conn.prepareStatement(
                            "UPDATE player_auction_limits SET bonus = ? WHERE uuid = ?")) {
                        update.setInt(1, normalizedBonus);
                        update.setString(2, playerUuid.toString());
                        update.executeUpdate();
                    }
                } else {
                    try (PreparedStatement insert = conn.prepareStatement(
                            "INSERT INTO player_auction_limits (uuid, bonus) VALUES (?, ?)")) {
                        insert.setString(1, playerUuid.toString());
                        insert.setInt(2, normalizedBonus);
                        insert.executeUpdate();
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to set auction bonus limit", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Integer> adjustAuctionBonusLimit(UUID playerUuid, int delta) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = connection()) {
                int current = 0;
                try (PreparedStatement ps = conn.prepareStatement("SELECT bonus FROM player_auction_limits WHERE uuid = ?")) {
                    ps.setString(1, playerUuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            current = rs.getInt("bonus");
                        }
                    }
                }
                int updated = Math.max(0, current + delta);
                boolean exists = current != 0;
                if (!exists) {
                    try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM player_auction_limits WHERE uuid = ?")) {
                        ps.setString(1, playerUuid.toString());
                        try (ResultSet rs = ps.executeQuery()) {
                            exists = rs.next();
                        }
                    }
                }
                if (exists) {
                    try (PreparedStatement update = conn.prepareStatement(
                            "UPDATE player_auction_limits SET bonus = ? WHERE uuid = ?")) {
                        update.setInt(1, updated);
                        update.setString(2, playerUuid.toString());
                        update.executeUpdate();
                    }
                } else {
                    try (PreparedStatement insert = conn.prepareStatement(
                            "INSERT INTO player_auction_limits (uuid, bonus) VALUES (?, ?)")) {
                        insert.setString(1, playerUuid.toString());
                        insert.setInt(2, updated);
                        insert.executeUpdate();
                    }
                }
                return updated;
            } catch (SQLException e) {
                throw new RuntimeException("Failed to adjust auction bonus limit", e);
            }
        }, executor);
    }

    private CompletableFuture<List<AuctionRecord>> loadAuctionsByWhere(String whereClause, UUID whereUuid, RegistryAccess registryAccess) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                    SELECT a.id, a.seller_uuid, COALESCE(sw.username, a.seller_uuid) AS seller_name,
                           a.item_nbt, a.quantity, a.start_price, a.buyout_price, a.current_bid,
                           a.bidder_uuid, bw.username AS bidder_name, a.expires_at, a.status
                    FROM auctions a
                    LEFT JOIN wallets sw ON sw.uuid = a.seller_uuid
                    LEFT JOIN wallets bw ON bw.uuid = a.bidder_uuid
                    WHERE """ + whereClause + " ORDER BY a.expires_at ASC";
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                if (whereUuid != null) {
                    ps.setString(1, whereUuid.toString());
                }
                try (ResultSet rs = ps.executeQuery()) {
                    List<AuctionRecord> auctions = new ArrayList<>();
                    while (rs.next()) {
                        auctions.add(mapAuctionRecord(rs, registryAccess));
                    }
                    return auctions;
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to load auctions", e);
            }
        }, executor);
    }

    private AuctionRecord mapAuctionRecord(ResultSet rs, RegistryAccess registryAccess) throws SQLException {
        UUID id = UUID.fromString(rs.getString("id"));
        UUID sellerUuid = UUID.fromString(rs.getString("seller_uuid"));
        String sellerName = rs.getString("seller_name");
        String itemNbt = rs.getString("item_nbt");
        ItemStack stack = ItemStackSerializationUtil.fromBase64(itemNbt, registryAccess);
        int quantity = Math.max(1, rs.getInt("quantity"));
        long startPrice = rs.getLong("start_price");
        Long buyoutPrice = rs.getObject("buyout_price") == null ? null : rs.getLong("buyout_price");
        long currentBid = rs.getLong("current_bid");
        String bidderRaw = rs.getString("bidder_uuid");
        UUID bidderUuid = bidderRaw == null || bidderRaw.isBlank() ? null : UUID.fromString(bidderRaw);
        String bidderName = rs.getString("bidder_name");
        long expiresAt = rs.getLong("expires_at");
        String status = rs.getString("status");
        return new AuctionRecord(
                id,
                sellerUuid,
                sellerName,
                stack,
                quantity,
                startPrice,
                buyoutPrice,
                currentBid,
                bidderUuid,
                bidderName,
                expiresAt,
                status
        );
    }

    private void enqueuePendingItem(Connection conn, UUID playerUuid, String itemNbt, int quantity, String reason) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO pending_deliveries (player_uuid, item_nbt, quantity, lc_amount, reason, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, itemNbt);
            ps.setInt(3, Math.max(1, quantity));
            ps.setNull(4, java.sql.Types.BIGINT);
            ps.setString(5, reason);
            ps.setLong(6, System.currentTimeMillis() / 1000L);
            ps.executeUpdate();
        }
    }

    private void enqueuePendingLc(Connection conn, UUID playerUuid, long amount, String reason) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO pending_deliveries (player_uuid, item_nbt, quantity, lc_amount, reason, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            ps.setString(1, playerUuid.toString());
            ps.setNull(2, java.sql.Types.VARCHAR);
            ps.setNull(3, java.sql.Types.INTEGER);
            ps.setLong(4, Math.max(0L, amount));
            ps.setString(5, reason);
            ps.setLong(6, System.currentTimeMillis() / 1000L);
            ps.executeUpdate();
        }
    }

    protected void upsertWallet(Connection conn, UUID uuid, String username) throws SQLException {
        try (PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO wallets (uuid, username, balance) VALUES (?, ?, 0) ON CONFLICT(uuid) DO NOTHING")) {
            insert.setString(1, uuid.toString());
            insert.setString(2, username);
            insert.executeUpdate();
        }
        try (PreparedStatement updateName = conn.prepareStatement("UPDATE wallets SET username = ? WHERE uuid = ?")) {
            updateName.setString(1, username);
            updateName.setString(2, uuid.toString());
            updateName.executeUpdate();
        }
    }

    private void ensureWalletExists(Connection conn, UUID uuid) throws SQLException {
        String fallbackUsername = uuid.toString().substring(0, 16);
        try (PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO wallets (uuid, username, balance) VALUES (?, ?, 0) ON CONFLICT(uuid) DO NOTHING")) {
            insert.setString(1, uuid.toString());
            insert.setString(2, fallbackUsername);
            insert.executeUpdate();
        }
    }

    private UUID findShopOwnerUuidForListing(Connection conn, UUID listingId) throws SQLException {
        try (PreparedStatement q = conn.prepareStatement("""
                SELECT owner_uuid FROM shops
                WHERE id = (SELECT shop_id FROM shop_listings WHERE id = ?)
                """)) {
            q.setString(1, listingId.toString());
            try (ResultSet rs = q.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                String ownerRaw = rs.getString("owner_uuid");
                return ownerRaw == null || ownerRaw.isBlank() ? null : UUID.fromString(ownerRaw);
            }
        }
    }

    private boolean isAdminShopForListing(Connection conn, UUID listingId) throws SQLException {
        try (PreparedStatement q = conn.prepareStatement("""
                SELECT s.is_admin FROM shop_listings sl
                JOIN shops s ON sl.shop_id = s.id
                WHERE sl.id = ?
                """)) {
            q.setString(1, listingId.toString());
            try (ResultSet rs = q.executeQuery()) {
                return rs.next() && rs.getBoolean("is_admin");
            }
        }
    }

    private boolean isAdminShop(Connection conn, UUID shopId) throws SQLException {
        try (PreparedStatement q = conn.prepareStatement("SELECT is_admin FROM shops WHERE id = ?")) {
            q.setString(1, shopId.toString());
            try (ResultSet rs = q.executeQuery()) {
                return rs.next() && rs.getBoolean("is_admin");
            }
        }
    }

    private void ensureColumnExists(Connection conn, String tableName, String columnName, String definition) throws SQLException {
        if (columnExists(conn, tableName, columnName)) {
            return;
        }
        try (Statement statement = conn.createStatement()) {
            statement.executeUpdate("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
        }
    }

    private boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();
        if (columnExists(metaData, tableName, columnName)) {
            return true;
        }
        if (columnExists(metaData, tableName.toLowerCase(), columnName)) {
            return true;
        }
        return columnExists(metaData, tableName.toUpperCase(), columnName);
    }

    private boolean columnExists(DatabaseMetaData metaData, String tableName, String columnName) throws SQLException {
        try (ResultSet rs = metaData.getColumns(null, null, tableName, null)) {
            while (rs.next()) {
                String col = rs.getString("COLUMN_NAME");
                if (col != null && col.equalsIgnoreCase(columnName)) {
                    return true;
                }
            }
        }
        return false;
    }
}
