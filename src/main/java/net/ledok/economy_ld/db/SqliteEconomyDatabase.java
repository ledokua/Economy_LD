package net.ledok.economy_ld.db;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class SqliteEconomyDatabase implements EconomyDatabase {
    private final Path dbPath;
    private final ExecutorService dbExecutor;

    public SqliteEconomyDatabase(Path dbPath, ExecutorService dbExecutor) {
        this.dbPath = dbPath;
        this.dbExecutor = dbExecutor;
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS wallets (
                            uuid TEXT PRIMARY KEY,
                            username TEXT NOT NULL,
                            balance INTEGER NOT NULL DEFAULT 0
                        )
                        """);
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to initialize SQLite schema", exception);
            }
        }, dbExecutor);
    }

    @Override
    public CompletableFuture<Void> close() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Long> getBalance(UUID playerUuid, String username) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = openConnection()) {
                ensureWallet(connection, playerUuid, username);
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT balance FROM wallets WHERE uuid = ?")) {
                    statement.setString(1, playerUuid.toString());
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (resultSet.next()) {
                            return resultSet.getLong("balance");
                        }
                    }
                }
                return 0L;
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to fetch balance", exception);
            }
        }, dbExecutor);
    }

    @Override
    public CompletableFuture<Void> setBalance(UUID playerUuid, String username, long balance) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = openConnection()) {
                ensureWallet(connection, playerUuid, username);
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE wallets SET username = ?, balance = ? WHERE uuid = ?")) {
                    statement.setString(1, username);
                    statement.setLong(2, balance);
                    statement.setString(3, playerUuid.toString());
                    statement.executeUpdate();
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to update balance", exception);
            }
        }, dbExecutor);
    }

    @Override
    public CompletableFuture<Boolean> transfer(UUID fromUuid, String fromUsername, UUID toUuid, String toUsername, long amount) {
        return CompletableFuture.supplyAsync(() -> {
            if (amount <= 0) {
                return false;
            }

            try (Connection connection = openConnection()) {
                connection.setAutoCommit(false);
                try {
                    ensureWallet(connection, fromUuid, fromUsername);
                    ensureWallet(connection, toUuid, toUsername);

                    long fromBalance = currentBalance(connection, fromUuid);
                    if (fromBalance < amount) {
                        connection.rollback();
                        return false;
                    }

                    updateBalance(connection, fromUuid, fromUsername, fromBalance - amount);
                    updateBalance(connection, toUuid, toUsername, currentBalance(connection, toUuid) + amount);
                    connection.commit();
                    return true;
                } catch (SQLException exception) {
                    connection.rollback();
                    throw exception;
                } finally {
                    connection.setAutoCommit(true);
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to transfer balance", exception);
            }
        }, dbExecutor);
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
    }

    private void ensureWallet(Connection connection, UUID uuid, String username) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT OR IGNORE INTO wallets (uuid, username, balance) VALUES (?, ?, 0)")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, username);
            statement.executeUpdate();
        }
    }

    private long currentBalance(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT balance FROM wallets WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getLong("balance");
                }
            }
        }
        return 0L;
    }

    private void updateBalance(Connection connection, UUID uuid, String username, long balance) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE wallets SET username = ?, balance = ? WHERE uuid = ?")) {
            statement.setString(1, username);
            statement.setLong(2, balance);
            statement.setString(3, uuid.toString());
            statement.executeUpdate();
        }
    }
}
