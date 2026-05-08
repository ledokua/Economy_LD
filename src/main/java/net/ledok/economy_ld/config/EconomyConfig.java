package net.ledok.economy_ld.config;

public final class EconomyConfig {
    public String storageType = "sqlite";
    public Sqlite sqlite = new Sqlite();
    public Mariadb mariadb = new Mariadb();
    public Currency currency = new Currency();

    public void sanitize() {
        if (storageType == null || storageType.isBlank()) {
            storageType = "sqlite";
        }
        storageType = storageType.toLowerCase();
        if (sqlite == null) {
            sqlite = new Sqlite();
        }
        if (mariadb == null) {
            mariadb = new Mariadb();
        }
        if (currency == null) {
            currency = new Currency();
        }
        sqlite.sanitize();
        mariadb.sanitize();
        currency.sanitize();
    }

    public static final class Sqlite {
        public String file = "economy_ld.db";

        private void sanitize() {
            if (file == null || file.isBlank()) {
                file = "economy_ld.db";
            }
        }
    }

    public static final class Mariadb {
        public String host = "localhost";
        public int port = 3306;
        public String database = "economy_ld";
        public String username = "root";
        public String password = "";

        private void sanitize() {
            if (host == null || host.isBlank()) {
                host = "localhost";
            }
            if (database == null || database.isBlank()) {
                database = "economy_ld";
            }
            if (username == null || username.isBlank()) {
                username = "root";
            }
            if (password == null) {
                password = "";
            }
            if (port <= 0 || port > 65535) {
                port = 3306;
            }
        }
    }

    public static final class Currency {
        public String name = "LeDok Coin";
        public String symbol = "LC";

        private void sanitize() {
            if (name == null || name.isBlank()) {
                name = "LeDok Coin";
            }
            if (symbol == null || symbol.isBlank()) {
                symbol = "LC";
            }
        }
    }
}
