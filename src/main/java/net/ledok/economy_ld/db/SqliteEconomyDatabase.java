package net.ledok.economy_ld.db;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public final class SqliteEconomyDatabase extends AbstractJdbcEconomyDatabase {
    private final String jdbcUrl;

    public SqliteEconomyDatabase(Path filePath, ExecutorService executor) {
        super(executor);
        this.jdbcUrl = "jdbc:sqlite:" + filePath.toAbsolutePath();
    }

    @Override
    protected Connection connection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    @Override
    protected String transactionsSchema() {
        return """
                CREATE TABLE IF NOT EXISTS transactions (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
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
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.completedFuture(null);
    }
}
