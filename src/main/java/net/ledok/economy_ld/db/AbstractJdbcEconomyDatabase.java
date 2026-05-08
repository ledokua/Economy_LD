package net.ledok.economy_ld.db;

import net.ledok.economy_ld.shop.ShopRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
                            stock       BIGINT,
                            FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE
                        )
                        """);
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
                 PreparedStatement ps = conn.prepareStatement("SELECT balance FROM wallets WHERE username = ?")) {
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
                 PreparedStatement ps = conn.prepareStatement("SELECT uuid FROM wallets WHERE username = ?")) {
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
}
