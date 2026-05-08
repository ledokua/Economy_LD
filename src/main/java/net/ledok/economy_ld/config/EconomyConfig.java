package net.ledok.economy_ld.config;

public class EconomyConfig {
    public String storageType = "sqlite";
    public SqliteConfig sqlite = new SqliteConfig();
    public MysqlConfig mysql = new MysqlConfig();
    public CurrencyConfig currency = new CurrencyConfig();

    public static final class SqliteConfig {
        public String file = "economy_ld.db";
    }

    public static final class MysqlConfig {
        public String host = "localhost";
        public int port = 3306;
        public String database = "economy_ld";
        public String username = "root";
        public String password = "";
    }

    public static final class CurrencyConfig {
        public String name = "LeDok Coin";
        public String symbol = "LC";
    }
}
