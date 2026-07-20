package com.newbudget.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database {
    private static final Path DB_PATH = Path.of("data", "budget.db");
    private static final String JDBC_URL = "jdbc:sqlite:" + DB_PATH;

    private Database() {
    }

    public static void initialize() {
        try {
            Files.createDirectories(DB_PATH.getParent());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create database directory", e);
        }

        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("""
                CREATE TABLE IF NOT EXISTS categories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    path TEXT NOT NULL UNIQUE,
                    parent_id INTEGER,
                    sort_order INTEGER NOT NULL,
                    default_type TEXT NOT NULL,
                    is_rollup INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY(parent_id) REFERENCES categories(id)
                )
                """);

            ensureColumnExists(connection, "categories", "is_master", "INTEGER NOT NULL DEFAULT 0");

            statement.execute("""
                CREATE TABLE IF NOT EXISTS monthly_actuals (
                    month TEXT NOT NULL,
                    category_id INTEGER NOT NULL,
                    actual_amount REAL NOT NULL,
                    PRIMARY KEY(month, category_id),
                    FOREIGN KEY(category_id) REFERENCES categories(id) ON DELETE CASCADE
                )
                """);

            statement.execute("""
                CREATE TABLE IF NOT EXISTS monthly_budgets (
                    month TEXT NOT NULL,
                    category_id INTEGER NOT NULL,
                    budget_amount REAL NOT NULL,
                    PRIMARY KEY(month, category_id),
                    FOREIGN KEY(category_id) REFERENCES categories(id) ON DELETE CASCADE
                )
                """);

            statement.execute("""
                CREATE TABLE IF NOT EXISTS monthly_classifications (
                    month TEXT NOT NULL,
                    category_id INTEGER NOT NULL,
                    type TEXT NOT NULL,
                    PRIMARY KEY(month, category_id),
                    FOREIGN KEY(category_id) REFERENCES categories(id) ON DELETE CASCADE
                )
                """);

            statement.execute("""
                CREATE TABLE IF NOT EXISTS monthly_balance_overrides (
                    month TEXT NOT NULL,
                    category_id INTEGER NOT NULL,
                    balance_amount REAL NOT NULL,
                    PRIMARY KEY(month, category_id),
                    FOREIGN KEY(category_id) REFERENCES categories(id) ON DELETE CASCADE
                )
                """);

            statement.execute("""
                CREATE TABLE IF NOT EXISTS monthly_hidden_categories (
                    month TEXT NOT NULL,
                    category_id INTEGER NOT NULL,
                    PRIMARY KEY(month, category_id),
                    FOREIGN KEY(category_id) REFERENCES categories(id) ON DELETE CASCADE
                )
                """);

            statement.execute("""
                CREATE TABLE IF NOT EXISTS accounts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL COLLATE NOCASE UNIQUE
                )
                """);

            statement.execute("""
                CREATE TABLE IF NOT EXISTS account_category_assignments (
                    account_id INTEGER NOT NULL,
                    category_id INTEGER NOT NULL UNIQUE,
                    PRIMARY KEY(account_id, category_id),
                    FOREIGN KEY(account_id) REFERENCES accounts(id) ON DELETE CASCADE,
                    FOREIGN KEY(category_id) REFERENCES categories(id) ON DELETE CASCADE
                )
                """);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize database", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(JDBC_URL);
    }

    private static void ensureColumnExists(Connection connection, String tableName, String columnName, String definition)
        throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet columns = metaData.getColumns(null, null, tableName, columnName)) {
            if (columns.next()) {
                return;
            }
        }

        try (Statement alterStatement = connection.createStatement()) {
            alterStatement.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
        }
    }
}
