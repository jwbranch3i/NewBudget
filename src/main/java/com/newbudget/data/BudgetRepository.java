package com.newbudget.data;

import com.newbudget.model.CategoryRecord;
import com.newbudget.model.CategoryType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class BudgetRepository {
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    public int findOrCreateCategory(
        String name,
        String path,
        Integer parentId,
        int sortOrder,
        CategoryType defaultType
    ) {
        Integer existingId = findCategoryByPath(path);
        if (existingId != null) {
            updateCategoryFromImport(existingId, name, parentId, sortOrder, defaultType);
            return existingId;
        }

        String sql = """
            INSERT INTO categories(name, path, parent_id, sort_order, default_type, is_rollup)
            VALUES (?, ?, ?, ?, ?, 0)
            """;
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, name);
            statement.setString(2, path);
            if (parentId == null) {
                statement.setNull(3, java.sql.Types.INTEGER);
            } else {
                statement.setInt(3, parentId);
            }
            statement.setInt(4, sortOrder);
            statement.setString(5, defaultType.name());
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create category", e);
        }

        throw new IllegalStateException("Failed to create category, no key returned");
    }

    public void markRollup(int categoryId) {
        String sql = "UPDATE categories SET is_rollup = 1 WHERE id = ?";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, categoryId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to mark rollup category", e);
        }
    }

    public void resetRollups() {
        String sql = "UPDATE categories SET is_rollup = 0";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to reset rollup categories", e);
        }
    }

    public void clearMonthActuals(YearMonth month) {
        String sql = "DELETE FROM monthly_actuals WHERE month = ?";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, toMonthKey(month));
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to clear monthly actuals", e);
        }
    }

    public void upsertMonthlyActual(YearMonth month, int categoryId, double amount) {
        String sql = """
            INSERT INTO monthly_actuals(month, category_id, actual_amount)
            VALUES (?, ?, ?)
            ON CONFLICT(month, category_id)
            DO UPDATE SET actual_amount = excluded.actual_amount
            """;
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, toMonthKey(month));
            statement.setInt(2, categoryId);
            statement.setDouble(3, amount);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to upsert monthly actual", e);
        }
    }

    public void upsertMonthlyBudget(YearMonth month, int categoryId, double amount) {
        String sql = """
            INSERT INTO monthly_budgets(month, category_id, budget_amount)
            VALUES (?, ?, ?)
            ON CONFLICT(month, category_id)
            DO UPDATE SET budget_amount = excluded.budget_amount
            """;
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, toMonthKey(month));
            statement.setInt(2, categoryId);
            statement.setDouble(3, amount);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to upsert monthly budget", e);
        }
    }

    public void setMonthlyClassification(YearMonth month, int categoryId, CategoryType type) {
        String sql = """
            INSERT INTO monthly_classifications(month, category_id, type)
            VALUES (?, ?, ?)
            ON CONFLICT(month, category_id)
            DO UPDATE SET type = excluded.type
            """;
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, toMonthKey(month));
            statement.setInt(2, categoryId);
            statement.setString(3, type.name());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to set monthly classification", e);
        }
    }

    public void ensureMonthlyClassificationsFromDefaults(YearMonth month) {
        String monthKey = toMonthKey(month);
        String sql = """
            INSERT INTO monthly_classifications(month, category_id, type)
            SELECT ?, c.id,
                COALESCE(
                    (SELECT mc.type
                     FROM monthly_classifications mc
                     WHERE mc.category_id = c.id AND mc.month < ?
                     ORDER BY mc.month DESC
                     LIMIT 1),
                    c.default_type
                )
            FROM categories c
            WHERE NOT EXISTS (
                SELECT 1
                FROM monthly_classifications mc2
                WHERE mc2.month = ? AND mc2.category_id = c.id
            )
            """;
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, monthKey);
            statement.setString(2, monthKey);
            statement.setString(3, monthKey);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to seed monthly classifications", e);
        }
    }

    public List<CategoryRecord> getAllCategories() {
        String sql = """
            SELECT id, name, path, parent_id, sort_order, default_type, is_rollup
            FROM categories
            ORDER BY sort_order, id
            """;
        List<CategoryRecord> categories = new ArrayList<>();
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                Integer parentId = resultSet.getObject("parent_id") == null
                    ? null
                    : resultSet.getInt("parent_id");
                categories.add(new CategoryRecord(
                    resultSet.getInt("id"),
                    resultSet.getString("name"),
                    resultSet.getString("path"),
                    parentId,
                    resultSet.getInt("sort_order"),
                    CategoryType.fromDb(resultSet.getString("default_type")),
                    resultSet.getInt("is_rollup") == 1
                ));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load categories", e);
        }
        return categories;
    }

    public Map<Integer, Double> getMonthlyActuals(YearMonth month) {
        return readAmountMap(
            "SELECT category_id, actual_amount AS amount FROM monthly_actuals WHERE month = ?",
            toMonthKey(month)
        );
    }

    public Map<Integer, Double> getMonthlyBudgets(YearMonth month) {
        return readAmountMap(
            "SELECT category_id, budget_amount AS amount FROM monthly_budgets WHERE month = ?",
            toMonthKey(month)
        );
    }

    public Map<Integer, Double> getCumulativeBudgetMinusActual(YearMonth month) {
        String sql = """
            SELECT c.id AS category_id,
                   COALESCE((SELECT SUM(b.budget_amount)
                             FROM monthly_budgets b
                             WHERE b.category_id = c.id AND b.month <= ?), 0)
                 - COALESCE((SELECT SUM(a.actual_amount)
                             FROM monthly_actuals a
                             WHERE a.category_id = c.id AND a.month <= ?), 0)
                   AS amount
            FROM categories c
            """;
        Map<Integer, Double> values = new HashMap<>();
        String monthKey = toMonthKey(month);
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, monthKey);
            statement.setString(2, monthKey);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    values.put(resultSet.getInt("category_id"), resultSet.getDouble("amount"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load cumulative balances", e);
        }
        return values;
    }

    public Map<Integer, CategoryType> getMonthlyClassifications(YearMonth month) {
        String sql = "SELECT category_id, type FROM monthly_classifications WHERE month = ?";
        Map<Integer, CategoryType> values = new HashMap<>();
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, toMonthKey(month));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    values.put(resultSet.getInt("category_id"), CategoryType.fromDb(resultSet.getString("type")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load monthly classifications", e);
        }
        return values;
    }

    public Optional<YearMonth> getLatestMonthWithData() {
        String sql = """
            SELECT MAX(month) AS month FROM (
                SELECT month FROM monthly_actuals
                UNION
                SELECT month FROM monthly_budgets
                UNION
                SELECT month FROM monthly_classifications
            )
            """;
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            String month = resultSet.getString("month");
            if (month == null || month.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(YearMonth.parse(month, MONTH_FORMAT));
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load latest month", e);
        }
    }

    public List<YearMonth> getAvailableMonths() {
        String sql = """
            SELECT month FROM (
                SELECT DISTINCT month FROM monthly_actuals
                UNION
                SELECT DISTINCT month FROM monthly_budgets
                UNION
                SELECT DISTINCT month FROM monthly_classifications
            )
            ORDER BY month
            """;
        List<YearMonth> months = new ArrayList<>();
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                months.add(YearMonth.parse(resultSet.getString("month"), MONTH_FORMAT));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load months", e);
        }
        return months;
    }

    public CategoryType getCategoryDefaultType(int categoryId) {
        String sql = "SELECT default_type FROM categories WHERE id = ?";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, categoryId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return CategoryType.fromDb(resultSet.getString("default_type"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load category type", e);
        }
        throw new IllegalStateException("Category not found: " + categoryId);
    }

    private Integer findCategoryByPath(String path) {
        String sql = "SELECT id FROM categories WHERE path = ?";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, path);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt("id");
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find category by path", e);
        }
        return null;
    }

    private void updateCategoryFromImport(
        int categoryId,
        String name,
        Integer parentId,
        int sortOrder,
        CategoryType defaultType
    ) {
        String sql = """
            UPDATE categories
            SET name = ?,
                parent_id = ?,
                sort_order = ?,
                default_type = ?
            WHERE id = ?
            """;
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            if (parentId == null) {
                statement.setNull(2, java.sql.Types.INTEGER);
            } else {
                statement.setInt(2, parentId);
            }
            statement.setInt(3, sortOrder);
            statement.setString(4, defaultType.name());
            statement.setInt(5, categoryId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update imported category", e);
        }
    }

    private Map<Integer, Double> readAmountMap(String sql, String month) {
        Map<Integer, Double> values = new HashMap<>();
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, month);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    values.put(resultSet.getInt("category_id"), resultSet.getDouble("amount"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load monthly values", e);
        }
        return values;
    }

    public static String toMonthKey(YearMonth month) {
        return month.format(MONTH_FORMAT);
    }
}
