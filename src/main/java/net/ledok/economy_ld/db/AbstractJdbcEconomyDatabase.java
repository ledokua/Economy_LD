package net.ledok.economy_ld.db;

import net.ledok.economy_ld.shop.ShopRecord;
import net.ledok.economy_ld.shop.ShopListing;
import net.ledok.economy_ld.util.ItemStackSerializationUtil;
import net.minecraft.core.BlockPos;
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
                            id              VARCHAR(36) PRIMARY KEY,
                            seller_uuid     VARCHAR(36) NOT NULL,
                            item_nbt        TEXT NOT NULL,
                            start_price     BIGINT NOT NULL,
                            current_bid     BIGINT NOT NULL,
                            bidder_uuid     VARCHAR(36),
                            expires_at      BIGINT NOT NULL,
                            status          VARCHAR(16) NOT NULL
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

                    long total = priceBuy * Math.max(1, quantity);
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

                    long total = priceSell * Math.max(1, quantity);
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
