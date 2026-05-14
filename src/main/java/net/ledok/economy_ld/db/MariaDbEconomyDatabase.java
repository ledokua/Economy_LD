package net.ledok.economy_ld.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public final class MariaDbEconomyDatabase extends AbstractJdbcEconomyDatabase {
    private final HikariDataSource dataSource;

    public MariaDbEconomyDatabase(String host, int port, String database, String username, String password, ExecutorService executor) {
        super(executor);
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mariadb://" + host + ":" + port + "/" + database);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(1);
        config.setPoolName("economy-ld-mariadb");
        this.dataSource = new HikariDataSource(config);
    }

    @Override
    protected Connection connection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    protected String transactionsSchema() {
        return """
                CREATE TABLE IF NOT EXISTS transactions (
                    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                    type        VARCHAR(16) NOT NULL,
                    from_uuid   VARCHAR(36),
                    to_uuid     VARCHAR(36),
                    amount      BIGINT NOT NULL,
                    note        VARCHAR(255),
                    timestamp   BIGINT NOT NULL
                )
                """;
    }

    @Override
    protected String pendingDeliveriesSchema() {
        return """
                CREATE TABLE IF NOT EXISTS pending_deliveries (
                    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
                    player_uuid  VARCHAR(36) NOT NULL,
                    item_nbt     TEXT,
                    quantity     INT,
                    lc_amount    BIGINT,
                    reason       VARCHAR(32) NOT NULL,
                    created_at   BIGINT NOT NULL,
                    expires_at   BIGINT NOT NULL DEFAULT 0
                )
                """;
    }

    @Override
    protected String upsertShopSql() {
        return """
                INSERT INTO shops (id, owner_uuid, is_admin, world, x, y, z, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    owner_uuid = VALUES(owner_uuid),
                    is_admin = VALUES(is_admin),
                    world = VALUES(world),
                    x = VALUES(x),
                    y = VALUES(y),
                    z = VALUES(z)
                """;
    }

    @Override
    protected void upsertWallet(Connection conn, UUID uuid, String username) throws SQLException {
        try (PreparedStatement upsert = conn.prepareStatement("""
                INSERT INTO wallets (uuid, username, balance)
                VALUES (?, ?, 0)
                ON DUPLICATE KEY UPDATE username = VALUES(username)
                """)) {
            upsert.setString(1, uuid.toString());
            upsert.setString(2, username);
            upsert.executeUpdate();
        }
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.runAsync(dataSource::close, executor);
    }
}
