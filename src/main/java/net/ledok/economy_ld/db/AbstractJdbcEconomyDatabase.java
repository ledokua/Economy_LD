package net.ledok.economy_ld.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
    public CompletableFuture<Long> getBalanceByUsername(String username) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement("SELECT balance FROM wallets WHERE username = ?")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong("balance");
                    }
                }
                return 0L;
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get balance for username=" + username, e);
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
